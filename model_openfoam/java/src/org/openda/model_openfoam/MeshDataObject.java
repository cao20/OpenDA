/* OpenDA v2.4.4 
* Copyright (c) 2018 OpenDA Association 
* All rights reserved.
* 
* This file is part of OpenDA. 
* 
* OpenDA is free software: you can redistribute it and/or modify 
* it under the terms of the GNU Lesser General Public License as 
* published by the Free Software Foundation, either version 3 of 
* the License, or (at your option) any later version. 
* 
* OpenDA is distributed in the hope that it will be useful, 
* but WITHOUT ANY WARRANTY; without even the implied warranty of 
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
* GNU Lesser General Public License for more details. 
* 
* You should have received a copy of the GNU Lesser General Public License
* along with OpenDA.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.openda.model_openfoam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


import org.openda.exchange.ArrayExchangeItem;
import org.openda.interfaces.IArray;
import org.openda.interfaces.IDataObject;
import org.openda.interfaces.IExchangeItem;
import org.openda.interfaces.IPrevExchangeItem.Role;
import org.openda.utils.Array;


/**
 * IDataObject to read samples generated by the OpenFOAM sample utility.
 *
 * @author Werner Kramer
 */
public class MeshDataObject implements IDataObject {


    private static final Logger logger = LoggerFactory.getLogger(MeshDataObject.class);

    private File file;
    private static String header =
        "/*-------------------------------*- OpenDA -*---------------------------------*`\n" +
                   "| =========                 |                                                 |\n" +
                   "| ``      /  F ield         | OpenFOAM: The Open Source CFD Toolbox           |\n" +
                   "|  ``    /   O peration     | Version:  3.0.1                                 |\n" +
                   "|   ``  /    A nd           | Web:      www.OpenFOAM.org                      |\n" +
                   "|    ``/     M anipulation  |                                                 |\n" +
                   "`*---------------------------------------------------------------------------*/\n".replace('`','\\');

    private static String foamFile =
        "FoamFile\n" +
        "{\n" +
        "    version     2.0;\n" +
        "    format      ascii;\n" +
        "    class       %s;\n" +
        "    object      %s;\n" +
        "}\n" +
        "// * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //\n\n".replace('`','"');

    private Map<String,Object> headerFields = new HashMap<>();
    private static String[] units = { "kg", "m", "s", "K", "mol", "A", "cd"};
    private Map<String,Integer> unitsMap = new LinkedHashMap<>();
	private static final String OPENFOAM_TIME_DIR = "OPENFOAM_TIME_DIR";
    //private int writePrecision = 6;

    // The geometric locations where the observations are made (x,y,z)
    //private UnstructuredMeshGeometryInfo geometryInfo;

    // The OpenFoam exchangeItems corresponding to the observations
    private Map<String,ArrayExchangeItem> exchangeItems = new HashMap<>();
    
    /**
     * Reads OpenFoam results generated by the sample utility.
     * 
     * @param workingDir the working directory.
     * @param arguments list of other arguments:
     * <ol>
     * <li>The name of the file containing the data 
     *      for this IoObject (relative to the working directory).</li>
     * </ol>
     */
    public void initialize(File workingDir, String[] arguments) {
        //IQuantityInfo[] coordinates = new QuantityInfo[3];
        //coordinates[0] = new QuantityInfo("x","m");
        //coordinates[1] = new QuantityInfo("y","m");
        //coordinates[2] = new QuantityInfo("z","m");
        //int[] dimensions = new int[3];
        //dimensions[0] = 0;  dimensions[0] = 0; dimensions[0] = 0;
        // geometryInfo = new UnstructuredMeshGeometryInfo(coordinates, new Array(dimensions));
        if ( arguments.length == 0 ) {
            throw new RuntimeException("No arguments are given when initializing.");
        }
		String filePath = arguments[0];
		if ( filePath.contains(OPENFOAM_TIME_DIR) ) {
			int start = filePath.indexOf(OPENFOAM_TIME_DIR)-1;
			File dir;
			if (start == -1 ) {
				dir = workingDir;
			} else {
				dir = new File(workingDir, filePath.substring(0,start) );
			}
			String latestTimeDir="0";
			File[] directoryItems = dir.listFiles();
			if (directoryItems != null) {
				double max = 0;
				for (File item : directoryItems )  {
					if ( item.isFile()) continue;
					try {
						double time = Double.parseDouble(item.getName());
						if ( time > max ) {
							max = time;
							latestTimeDir = item.getName();
						}
					} catch (NumberFormatException e) {
						logger.trace("Directory cannnot be parsed as time:" + item.getName() );
					}
				}
			} else {
				throw new RuntimeException("Directory does not exist: " + dir.getAbsolutePath());
			}
			filePath = filePath.replaceFirst(OPENFOAM_TIME_DIR,latestTimeDir);
		}

		this.file = new File(workingDir, filePath);


		if (this.file.exists()) {
            logger.debug("Reading " + this.file.getName());
            try {
                Scanner scanner;
                if (this.file.getName().endsWith(".gz")) {
                    GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(this.file));
                    scanner = new Scanner(gzis);
                } else {
                    scanner = new Scanner(this.file);
                }
				scanner.useLocale(Locale.US);
				// search for FoamFile dict
                String dictName = scanner.findWithinHorizon("FoamFile", 0);
                if (dictName == null) {
                    logger.error("Unexpected file format for file: " + this.file.getPath(), new RuntimeException());
                }
                logger.debug(dictName);
                scanner.useDelimiter(";|\\s+");
                while (scanner.hasNext()) {
                    String key = scanner.next();
                    if (key.isEmpty()) {
                        continue;
                    } else if ("{".equals(key)) {
                        continue;
                    } else if ("}".equals(key)) {
                        break;
                    } else if ("version".equals(key)) {
                        Double value = scanner.nextDouble();
                        this.headerFields.put(key, value);
                    } else if ("format".equals(key)) {
                        String value = scanner.next();
                        this.headerFields.put(key, value);
                    } else if ("class".equals(key)) {
                        String value = scanner.next();
                        this.headerFields.put(key, value);
                    } else if ("location".equals(key)) {
                        String value = scanner.next();
                        this.headerFields.put(key, value);
                    } else if ("object".equals(key)) {
                        String value = scanner.next();
                        this.headerFields.put(key, value);
                    } else {
                        throw new RuntimeException("Unknown field in FoamFile dict: " + key);
                    }
                }
                logger.debug(headerFields.toString());
                scanner.findWithinHorizon("dimensions", 0);
                String dimensions = scanner.nextLine();
                logger.debug(dimensions);
                this.dimensionsFromString(dimensions);
                scanner.findWithinHorizon("internalField", 0);
                String nonuniform = scanner.next().trim();
                if ( !nonuniform.equalsIgnoreCase("nonuniform") ) {
					throw new RuntimeException("internalFields can only be of nonuniform type, current type is '" + nonuniform + "'");
				}
				String list = scanner.next();
                int fieldLength = scanner.nextInt();
                logger.debug(nonuniform + fieldLength);
                scanner.findWithinHorizon("\\(", 0);
                if (list.equals("List<scalar>")) {
                    double[] data = new double[fieldLength];
                    for (int l = 0; l < fieldLength; l++) {
                        data[l] = scanner.nextDouble();
                    }
                    String id = (String) headerFields.get("object");
                    ArrayExchangeItem exchangeItem = new ArrayExchangeItem(id, Role.InOut);
                    exchangeItem.setArray(new Array(data));
                    exchangeItems.put(id, exchangeItem);
                } else if (list.equals("List<vector>")) {
                    scanner.useDelimiter("\\s+|\\(|\\)");
                    double[][] data = new double[3][];
                    data[0] = new double[fieldLength];
                    data[1] = new double[fieldLength];
                    data[2] = new double[fieldLength];
                    for (int l = 0; l < fieldLength; l++) {
                        scanner.next();
                        for (int i = 0; i < 3; i++) {
                            //logger.debug(scanner.next());
                            data[i][l] = scanner.nextDouble();
                        }
                        scanner.next();
                    }
                    String id = (String) headerFields.get("object");
                    for (int i = 0; i < 3; i++) {
                        String myId = id + (i+1);
                        ArrayExchangeItem exchangeItem = new ArrayExchangeItem(myId, Role.InOut);
                        exchangeItems.put(myId, exchangeItem);
                        exchangeItem.setArray(new Array(data[i]));
                    }
				}
				scanner.close();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        } else {
            // File will be created on finish()
            logger.debug("File " + this.file.getName() + " does not exist create one");
            this.dimensionsFromString("[0 0 0 0 0 0 0]");
            String id = arguments[1];
            this.headerFields.put("object", id);
            if (arguments[2].equals("scalar")) {
                ArrayExchangeItem exchangeItem = new ArrayExchangeItem(id, Role.InOut);
                exchangeItem.setArray(new Array(1));
                exchangeItems.put(id, exchangeItem);
            } else if (arguments[2].equals("vector")) {
                for (int i = 0; i < 3; i++) {
                    String comp = Integer.toString(i + 1);
                    ArrayExchangeItem exchangeItem = new ArrayExchangeItem(id + comp, Role.InOut);
                    exchangeItems.put(id + comp, exchangeItem);
                    exchangeItem.setArray(new Array(1));
                }
            }
        }

    }

    /** {@inheritDoc}
     */
    public String[] getExchangeItemIDs() {
        return this.exchangeItems.keySet().toArray(new String[exchangeItems.size()]);
    }

    /** {@inheritDoc}
     */
    public String[] getExchangeItemIDs(Role role) {
        // only Output from the model or observer is implemented
        if (role == Role.InOut) {
           return getExchangeItemIDs();
        }
        else {
            return null;
        }
    }

    /** {@inheritDoc}
     */
    public IExchangeItem getDataObjectExchangeItem(String exchangeItemID) {
        return this.exchangeItems.get(exchangeItemID);
    }

	/** {@inheritDoc}
	 */
    public void finish() {
        BufferedWriter writer;
        try {
            FileOutputStream outfile = new FileOutputStream((this.file));
            if (this.file.getName().endsWith(".gz")) {
                GZIPOutputStream gzip = new GZIPOutputStream((outfile));
                writer = new BufferedWriter(new OutputStreamWriter(gzip, "UTF-8"));
            } else {
                writer = new BufferedWriter((new OutputStreamWriter(outfile, "UTF-8")));
            }
            writer.write(header);
            writer.write(String.format(foamFile,this.headerFields.get("class"),this.headerFields.get("object") ));
            writer.write(this.dimensionsToString());
            Integer nrItems =this.exchangeItems.size();
            // output exchange item
            if ( nrItems == 1 ) {
                writer.write("internalField nonuniform List<scalar>\n");
                String id = (String) this.headerFields.get("object");
                IArray array = this.exchangeItems.get(id).getArray();
                double[] data = array.getValuesAsDoubles();
                writer.write(String.format("%d\n(\n",data.length));
                for ( double value : data ) {
                    writer.write( Double.toString(value) + "\n");
                }
                writer.write(")\n");
            } else if (nrItems == 3) {
                writer.write("internalField   nonuniform List<vector>\n");
                String id = (String) this.headerFields.get("object");
                double[][] data = new double[3][];
                for (int i = 0 ; i < 3 ; i++) {
                    String comp = Integer.toString(i + 1);
                    IArray array = this.exchangeItems.get(id + comp).getArray();
                    data[i] = array.getValuesAsDoubles();
                }
                writer.write(String.format("%d\n(\n",data[0].length));
                for (int i=0; i < data[0].length ;i++) {
                    writer.write(String.format("(%s %s %s)\n", Double.toString(data[0][i]), Double.toString(data[1][i]), Double.toString(data[2][i]) )  );
                }
                writer.write(")\n");
            } else {
                throw new RuntimeException(String.format("%s: Can only store 1 exchange item as List<scalar> or 3 exchange items as List<vec" +
                    "tor>.\nCannot store %d exchange items.",this.getClass().toString() , nrItems));
            }
            writer.close();
        } catch (IOException e) {

            throw new RuntimeException(e.getMessage());
        }
    }

    private String dimensionsToString() {
        return String.format("dimensions      [%d %d %d %d %d %d %d]\n\n",(Object[]) this.unitsMap.values().toArray() );
    }

    private void dimensionsFromString(String source) {
        String cleanSource = source.substring(source.indexOf('[')+1, source.indexOf(']') ).trim();
        List<String> items = Arrays.asList(cleanSource.split("\\s"));
        for (int i = 0; i < units.length; i++) {
            unitsMap.put(units[i], Integer.parseInt(items.get(i)));
        }
    }

//    private String dimensionsToUnits() {
//        final Set<Map.Entry<String,Integer>> entries = this.unitsMap.entrySet();
//
//        for (Map.Entry<String, Integer> entry : entries) {
//            String unitName = entry.getKey();
//            Integer exponent = entry.getValue();
//            if ( exponent == 0 ) {
//                continue;
//            } else if ( exponent < 0) {
//				continue;
//            }
//        }
//		return "undef";
//    }


}

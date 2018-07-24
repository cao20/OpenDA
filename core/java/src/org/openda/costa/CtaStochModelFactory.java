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

package org.openda.costa;

import org.openda.interfaces.IStochModelFactory;
import org.openda.interfaces.IStochModelInstance;
import org.openda.interfaces.IStochModelPostProcessor;

import java.io.File;
import java.io.IOException;

public class CtaStochModelFactory extends CtaObject implements IStochModelFactory {

    private String modelClassConfigFilePath;
    private String modelConfigFilePath;

	public void initialize(File workingDir, String[] arguments){

        String configString = arguments[0];
        String[] configStringParts = configString.split(";");
		if (!(configStringParts.length == 2)){
		  throw new RuntimeException("The config must contains 2 parts 'modelClass' and 'model' however the number of parts is "
				  +  configString.length());
		}
        String classConfigFileName = parseValue(configStringParts[0], "modelClass");
        File classConfigFile = new File(workingDir, classConfigFileName);
        try {
            this.modelClassConfigFilePath = classConfigFile.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalStateException("Could not compose full path for file "
                    + workingDir.getAbsolutePath() + " and " + configString);
        }
        if (!classConfigFile.exists()) {
            throw new RuntimeException("Model class config file does not exist (file: "
                    + this.modelClassConfigFilePath + ")");
        }

        this.modelConfigFilePath = "";
        String configFileName = parseValue(configStringParts[1], "model");
        if (! (configFileName == null || configFileName.equals("") ) ) {
            File modelConfigFile = new File(workingDir, configFileName);
            try {
                this.modelConfigFilePath = modelConfigFile.getCanonicalPath();
            } catch (IOException e) {
                throw new IllegalStateException("Could not compose full path for file "
                        + workingDir.getAbsolutePath() + " and " + configString);
            }
            if (!modelConfigFile.exists()) {
                throw new RuntimeException("Model config file does not exist (file: "
                        +  this.modelConfigFilePath + ")");
            }
        }
    }

    public IStochModelInstance getInstance(OutputLevel outputLevel) {
        return new CtaOpenDaModel(modelClassConfigFilePath, modelConfigFilePath);
    }

    public IStochModelPostProcessor getPostprocessorInstance(File instanceDir) {
        throw new UnsupportedOperationException("org.costa.CtaStochModelFactory.getPostprocessorInstance(): Not implemented yet.");
    }

	public void finish() {
		// no action needed (yet);
	}

	private String parseValue(String configString, String attribute) {
        String valueKey = attribute + "=\"";
        int valuePos = configString.indexOf(valueKey);
        if ( valuePos < 0 ) {
            throw new IllegalArgumentException("configString\n\t\"" + configString +
                    "\ndoes not contain attribute \"" + attribute +  "\"");
        }
        String valueString = configString.substring(valuePos + valueKey.length());
        valueString = valueString.substring(0,valueString.indexOf("\""));
        return valueString;
    }

}



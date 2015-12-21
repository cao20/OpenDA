! Advanced EFDC Hydraulic structure ! GEOSR. 2011. 12. JGCHO
!
      SUBROUTINE SCANGTAB
      USE GLOBAL
      CHARACTER*11 INFILE
      INTEGER I,J
      INTEGER NOELE1,NOGELE1
			 
      WRITE(*,'(A)')'SCANNING INPUT FILE: GATETAB.INP'
      INFILE='GATETAB.INP'

      OPEN(1,FILE='GATETAB.INP',STATUS='UNKNOWN')  
			
      MAXNOELE=0
      MAXNOGELE=0
      READ(1,*);READ(1,*);READ(1,*); !HEADER
      DO I=1,16
        READ(1,*)NOELE1,NOGELE1
        IF(NOELE1.GE.MAXNOELE) MAXNOELE=NOELE1
        IF(NOGELE1.GE.MAXNOGELE) MAXNOGELE=NOGELE1
        DO J=1,NOELE1+1
          READ(1,*)
        ENDDO
      ENDDO
      CLOSE(1)
      RETURN
      END SUBROUTINE
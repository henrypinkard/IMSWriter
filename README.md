# IMSWriter
Java library for writing Imaris .ims files. The Java library calls HDF Java for writing an the HDF files (IMS files are just HDF5 files with a specific directory structure and a different extension). The Java HDF code itself calls native libraries. Included for convenience in the "lib" folder of this project are the binaries for OSX and Windows 64 bit. Other libraries that match with the verion this code was tested against can be found at https://support.hdfgroup.org/ftp/HDF5/prev-releases/HDF-JAVA/HDF-JAVA-2.7/bin/

This repo also contains a Python wrapper for calling the Java library. See example.py for an example of how to use it.

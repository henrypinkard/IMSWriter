from py4j.java_gateway import JavaGateway
import os, inspect, time
import numpy as np

class ImarisJavaWrapper:
    """
    Python wrapper class for ImarisWriter.jar, which itself calls HDF Java libraries
    """

    def __init__(self, dir, name, shape, byte_depth, num_channels, num_frames, pixel_size_xy, pixel_size_z):
        """
        :param dir: directory for saving
        :param name: filename
        :param shape: (width, height, z)
        :param num_channels:
        :param num_frames:
        :param pixel_size_xy: um/pixel
        :param pixel_size_z: um/pixel
        """
        filename = os.sep.join([dir, name]) + '.ims'
        if os.path.isfile(filename):
            print('Deleting existing file: {}'.format(filename))
            os.remove(filename)
        #get the path of the current file because lib files are found relative to it
        lib_path = os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))) + os.sep + 'lib'
        self.javagateway = JavaGateway.launch_gateway(classpath=os.pathsep.join([
            os.sep.join([lib_path, 'jhdf5obj.jar']), os.sep.join([lib_path, 'jhdf5.jar']),
            os.sep.join([lib_path, 'jhdfobj.jar']), os.sep.join([lib_path, 'ImarisWriter.jar'])]),
            javaopts=['-Xmx4096m', '-Djava.library.path='+lib_path], die_on_exit=True)
        self.writer = self.javagateway.jvm.main.java.ImarisWriter(dir, name, shape[0], shape[1], byte_depth, shape[2],
                                                            num_channels, num_frames, pixel_size_xy, pixel_size_z)

    def __enter__(self):
        return self

    def __exit__(self):
        self.writer.close()

    def write_z_slice(self, pixels, slice, channel, frame, timestamp):
        """
        Write a single slice of a single channel to the imaris file
        :param pixels: numpy array of pixel data
        :param slice: slice index
        :param channel: channel index
        :param frame: frame index
        :param date:
        :param timestamp:
        :return:
        """
        #delay here so that RAM usage doesn't go out of control
        while self.writer.getNumSlicesInQueue() > 10: #10 is abitrary, could be tuned
            time.sleep(0.005)
        self.writer.addImage(pixels.tobytes(), slice, channel, frame, timestamp)
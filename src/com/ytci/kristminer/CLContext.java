package com.ytci.kristminer;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CLUtil.*;
import static org.lwjgl.system.MemoryUtil.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CLCreateContextCallback;
import org.lwjgl.opencl.CLDevice;
import org.lwjgl.opencl.CLPlatform;

import java.io.Closeable;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an OpenCL context.
 *
 * @author Lignum
 */
public final class CLContext implements Closeable {
    /** Contains all GPUs capable of OpenCL */
    private List<CLDevice> devices = new ArrayList<CLDevice>();

    /** A pointer to our OpenCL context */
    private long clContext;

    /** A pointer to our command queue */
    private long commandQueue;

    /** OpenCL will call this in case of an error */
    private static final CLCreateContextCallback createContextCallback = new CLCreateContextCallback() {
        @Override
        public void invoke(long errinfo, long private_info, long cb, long user_data) {
            System.err.println("Error during OpenCL context creation: " + memDecodeUTF8(errinfo));
        }
    };

    /**
     * Constructs an OpenCL context. This is expensive, so be careful!
     */
    public CLContext() {
        // LWJGL seems to create an OpenCL context automatically?
        // Uncomment if it complains...
        // CL.create();

        CLPlatform platform = CLPlatform.getPlatforms().get(0);
        devices = platform.getDevices(CL_DEVICE_TYPE_GPU);

        PointerBuffer ptrCLProps = BufferUtils.createPointerBuffer(3);
        ptrCLProps.put(CL_CONTEXT_PLATFORM); // Set the platform...
        ptrCLProps.put(platform); // ...to our platform
        ptrCLProps.put(0); // Terminate the property list
        ptrCLProps.flip(); // Must be done on all buffers passed to OpenCL/OpenGL.

        IntBuffer errorCode = BufferUtils.createIntBuffer(1); // Will store any error codes returned by
                                                              // clCreateContext

        PointerBuffer ptrDevices = BufferUtils.createPointerBuffer(1);
        ptrDevices.put(devices.get(0)); // Use the first device we can find.
                                        // TODO: Make this configurable?
        ptrDevices.flip(); // Once again, this is required by LWJGL.

        clContext = clCreateContext(ptrCLProps, ptrDevices, createContextCallback, NULL, errorCode);
        checkCLError(errorCode);
    }

    /**
     * Calls clRelease* on all used OpenCL objects.
     */
    @Override
    public void close() {
        // Clean up after ourselves
        clReleaseContext(clContext);
    }
}

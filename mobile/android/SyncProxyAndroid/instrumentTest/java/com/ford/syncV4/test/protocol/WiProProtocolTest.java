package com.ford.syncV4.test.protocol;

import android.test.InstrumentationTestCase;

import com.ford.syncV4.protocol.IProtocolListener;
import com.ford.syncV4.protocol.ProtocolFrameHeader;
import com.ford.syncV4.protocol.ProtocolFrameHeaderFactory;
import com.ford.syncV4.protocol.ProtocolMessage;
import com.ford.syncV4.protocol.WiProProtocol;
import com.ford.syncV4.protocol.enums.FrameType;
import com.ford.syncV4.protocol.enums.SessionType;
import com.ford.syncV4.util.BitConverter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Created by Andrew Batutin on 8/21/13.
 */
public class WiProProtocolTest extends InstrumentationTestCase {

    public static final byte VERSION = (byte) 2;
    public static final int MESSAGE_ID = 1;
    public static final byte SESSION_ID = (byte) 48;
    public static final byte FRAME_SEQUENCE_NUMBER = (byte) 1;
    Method currentCheckMethod;
    private WiProProtocol sut;
    private ProtocolFrameHeader currentFrameHeader;
    private byte[] currentData;

    public WiProProtocolTest() {
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
        sut = new WiProProtocol(mock(IProtocolListener.class)) {


            @Override
            public void SendMessage(ProtocolMessage protocolMsg) {
                prepareMockItems();
                super.SendMessage(protocolMsg);
            }

            private void prepareMockItems() {
                _messageLocks = mock(Hashtable.class);
                when(_messageLocks.get(anyByte())).thenReturn("mockLock");
                doThrow(new IllegalStateException("should not get protocol error")).when(_protocolListener).onProtocolError(anyString(), any(Exception.class));
            }

            @Override
            protected void handleProtocolFrameToSend(ProtocolFrameHeader header, byte[] data, int offset, int length) {
                super.handleProtocolFrameToSend(header, data, offset, length);
                callCheck(currentCheckMethod, data, header, offset, length);
            }
        };
        sut.setVersion((byte) 2);
    }

    public void testSendMobileNavSmallFrameProtocolMessageSucceed() throws Exception {
        ProtocolMessage message = generateMobileNavProtocolMessage(8);
        currentData = generateByteArray(8);
        currentFrameHeader = ProtocolFrameHeaderFactory.createSingleSendData(SessionType.Mobile_Nav, SESSION_ID, currentData.length, MESSAGE_ID, VERSION);
        currentCheckMethod = generateCurrentCheckMethod("checkCurrentArgumentsSmallFrame");
        sut.SendMessage(message);
    }

    public void testSendMobileNavFirstBigFrameProtocolMessageSucceed() throws Exception {
        ProtocolMessage message = generateMobileNavProtocolMessage(WiProProtocol.MAX_DATA_SIZE * 2);
        currentData = generateByteArray(WiProProtocol.MAX_DATA_SIZE * 2);
        currentFrameHeader = ProtocolFrameHeaderFactory.createMultiSendDataFirst(SessionType.Mobile_Nav, SESSION_ID, MESSAGE_ID, VERSION);
        currentCheckMethod = generateCurrentCheckMethod("checkCurrentArgumentsFirstBigFrame");
        sut.SendMessage(message);
    }

    public void testSendMobileNavConsecutiveBigFrameProtocolMessageSucceed() throws Exception {
        ProtocolMessage message = generateMobileNavProtocolMessage(WiProProtocol.MAX_DATA_SIZE * 3);
        currentData = generateByteArray(WiProProtocol.MAX_DATA_SIZE);
        currentFrameHeader = ProtocolFrameHeaderFactory.createMultiSendDataRest(SessionType.Mobile_Nav, SESSION_ID, currentData.length, FRAME_SEQUENCE_NUMBER, MESSAGE_ID, VERSION);
        currentCheckMethod = generateCurrentCheckMethod("checkCurrentArgumentsSecondBigFrame");
        sut.SendMessage(message);
    }

    private Method generateCurrentCheckMethod(String checkMethodName) throws NoSuchMethodException {
        Class[] parameterTypes = new Class[4];
        parameterTypes[0] = byte[].class;
        parameterTypes[1] = ProtocolFrameHeader.class;
        parameterTypes[2] = int.class;
        parameterTypes[3] = int.class;
        return WiProProtocolTest.class.getMethod(checkMethodName, parameterTypes);
    }

    private ProtocolMessage generateMobileNavProtocolMessage(int i) {
        byte[] frame = generateByteArray(i);
        ProtocolMessage message = new ProtocolMessage();
        message.setData(frame);
        message.setVersion((byte) 2);
        message.setSessionID((byte) 48);
        message.setSessionType(SessionType.Mobile_Nav);
        return message;
    }

    private byte[] generateByteArray(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) i;
        }
        return b;
    }

    private void callCheck(Method method, byte[] data, ProtocolFrameHeader messageHeader, int offset, int length) {
        Object[] parameters = new Object[4];
        parameters[0] = data;
        parameters[1] = messageHeader;
        parameters[2] = offset;
        parameters[3] = length;
        try {
            method.invoke(WiProProtocolTest.this, parameters);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            reconstructAssertionError(e);
        }
    }

    private void reconstructAssertionError(InvocationTargetException e) {
        String methodName = getMethodName(e);
        assertNull("Should not get here. " + e.getCause().getMessage() + " " + methodName, e);
    }

    private String getMethodName(InvocationTargetException e) {
        String methodName = "";
        if (e.getCause() != null && e.getCause().getStackTrace() != null && e.getCause().getStackTrace().length > 0) {
            for (int i = 0; i < e.getCause().getStackTrace().length; i++) {
                if (e.getCause().getStackTrace()[i].toString().contains(this.getClass().getName())) {
                    methodName = e.getCause().getStackTrace()[i].toString();
                    break;
                }
            }
        }
        return methodName;
    }

    public void checkCurrentArgumentsSmallFrame(byte[] data, ProtocolFrameHeader messageHeader, int offset, int length) throws Exception {
        assertTrue(Arrays.equals(currentData, data));
        assertEquals(currentFrameHeader, messageHeader);
    }

    public void checkCurrentArgumentsFirstBigFrame(byte[] data, ProtocolFrameHeader messageHeader, int offset, int length) throws Exception {
        if (messageHeader.getFrameType() == FrameType.First) {
            byte[] firstFrameData = getFirstFrameData(currentData);
            assertTrue("Arrays should be equal.", Arrays.equals(firstFrameData, data));
            assertEquals("SessionType should be equal.", currentFrameHeader.getSessionType(), messageHeader.getSessionType());
            assertEquals("FrameType should be equal.", currentFrameHeader.getFrameType(), messageHeader.getFrameType());
            assertEquals("FrameData should be equal.", currentFrameHeader.getFrameData(), messageHeader.getFrameData());
            assertEquals("Version should be equal.", currentFrameHeader.getVersion(), messageHeader.getVersion());
            assertEquals("Compressed state should be equal.", currentFrameHeader.isCompressed(), messageHeader.isCompressed());
            assertEquals("Frame headers should be equal.", currentFrameHeader.getDataSize(), messageHeader.getDataSize());
            assertEquals("DataSize should be equal.", currentFrameHeader.getMessageID(), messageHeader.getMessageID());
            assertEquals("Frame headers should be equal.", currentFrameHeader.getSessionID(), messageHeader.getSessionID());
        }
    }

    public void checkCurrentArgumentsSecondBigFrame(byte[] data, ProtocolFrameHeader messageHeader, int offset, int length) throws Exception {
        if (messageHeader.getFrameType() == FrameType.Consecutive && messageHeader.getFrameData() == (byte) 1) {
            assertTrue("Length of data should be less then WiProProtocol.MAX_DATA_SIZE", length <= WiProProtocol.MAX_DATA_SIZE);
            byte[] res = new byte[length];
            System.arraycopy(data, 0, res, 0, length);
            assertTrue("Arrays should be equal.", Arrays.equals(currentData, res));
            assertTrue("Offset should be 0 for second frame", offset == 0);
            assertEquals("SessionType should be equal.", currentFrameHeader.getSessionType(), messageHeader.getSessionType());
            assertEquals("FrameType should be equal.", currentFrameHeader.getFrameType(), messageHeader.getFrameType());
            assertEquals("FrameData should be equal.", currentFrameHeader.getFrameData(), messageHeader.getFrameData());
            assertEquals("Version should be equal.", currentFrameHeader.getVersion(), messageHeader.getVersion());
            assertEquals("Compressed state should be equal.", currentFrameHeader.isCompressed(), messageHeader.isCompressed());
            assertEquals("Frame headers should be equal.", currentFrameHeader.getDataSize(), messageHeader.getDataSize());
            assertEquals("DataSize should be equal.", currentFrameHeader.getMessageID(), messageHeader.getMessageID());
            assertEquals("Frame headers should be equal.", currentFrameHeader.getSessionID(), messageHeader.getSessionID());
        }
    }

    private byte[] getFirstFrameData(byte[] data) {
        int frameCount = data.length / WiProProtocol.MAX_DATA_SIZE;
        if (data.length % WiProProtocol.MAX_DATA_SIZE > 0) {
            frameCount++;
        }
        byte[] firstFrameData = new byte[8];
        // First four bytes are data size.
        System.arraycopy(BitConverter.intToByteArray(data.length), 0, firstFrameData, 0, 4);
        // Second four bytes are frame count.
        System.arraycopy(BitConverter.intToByteArray(frameCount), 0, firstFrameData, 4, 4);
        return firstFrameData;
    }
}

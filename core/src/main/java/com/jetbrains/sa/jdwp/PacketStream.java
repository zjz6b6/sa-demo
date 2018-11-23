/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 * Copyright (C) 2018 JetBrains s.r.o.
 *
 * This program is free software; you can redistribute and/or modify it under
 * the terms of the GNU General Public License v2 with Classpath Exception.
 * The text of the license is available in the file LICENSE.TXT.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See LICENSE.TXT for more details.
 *
 * You may contact JetBrains s.r.o. at Na Hřebenech II 1718/10, 140 00 Prague,
 * Czech Republic or at legal@jetbrains.com.
 */

package com.jetbrains.sa.jdwp;

import com.jetbrains.sa.jdi.*;
import com.sun.jdi.InternalException;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class PacketStream {
    final VirtualMachineImpl vm;
    private int inCursor = 0;
    final Packet pkt;
    ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
    private boolean isCommitted = false;

    PacketStream(VirtualMachineImpl vm, int id, int cmdSet, int cmd) {
        this.vm = vm;
        this.pkt = new Packet();
        pkt.id = id;
        pkt.cmdSet = (short) cmdSet;
        pkt.cmd = (short) cmd;
    }

    PacketStream(VirtualMachineImpl vm, Packet pkt) {
        this.vm = vm;
        this.pkt = pkt;
        this.isCommitted = true; /* read only stream */
    }

    int id() {
        return pkt.id;
    }

    void send() {
        if (!isCommitted) {
            pkt.data = dataStream.toByteArray();
            vm.sendToTarget(pkt);
            isCommitted = true;
        }
    }

//    void waitForReply() throws JDWPException {
//        if (!isCommitted) {
//            throw new InternalException("waitForReply without send");
//        }
//
//        vm.waitForTargetReply(pkt);
//
//        if (pkt.errorCode != Packet.ReplyNoError) {
//            throw new JDWPException(pkt.errorCode);
//        }
//    }

    public void writeBoolean(boolean data) {
        if (data) {
            dataStream.write(1);
        } else {
            dataStream.write(0);
        }
    }

    public void writeByte(byte data) {
        dataStream.write(data);
    }

    public void writeChar(char data) {
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeShort(short data) {
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeInt(int data) {
        dataStream.write((byte) ((data >>> 24) & 0xFF));
        dataStream.write((byte) ((data >>> 16) & 0xFF));
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeLong(long data) {
        dataStream.write((byte) ((data >>> 56) & 0xFF));
        dataStream.write((byte) ((data >>> 48) & 0xFF));
        dataStream.write((byte) ((data >>> 40) & 0xFF));
        dataStream.write((byte) ((data >>> 32) & 0xFF));

        dataStream.write((byte) ((data >>> 24) & 0xFF));
        dataStream.write((byte) ((data >>> 16) & 0xFF));
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeFloat(float data) {
        writeInt(Float.floatToIntBits(data));
    }

    public void writeDouble(double data) {
        writeLong(Double.doubleToLongBits(data));
    }

    void writeID(int size, long data) {
        switch (size) {
            case 8:
                writeLong(data);
                break;
            case 4:
                writeInt((int) data);
                break;
            case 2:
                writeShort((short) data);
                break;
            default:
                throw new UnsupportedOperationException("JDWP: ID size not supported: " + size);
        }
    }

    void writeNullObjectRef() {
        writeObjectRef(0);
    }

    public void writeObjectRef(long data) {
        writeID(vm.sizeofObjectRef, data);
    }

    void writeClassRef(long data) {
        writeID(vm.sizeofClassRef, data);
    }

    void writeMethodRef(long data) {
        writeID(vm.sizeofMethodRef, data);
    }

    void writeFieldRef(long data) {
        writeID(vm.sizeofFieldRef, data);
    }

    void writeFrameRef(long data) {
        writeID(vm.sizeofFrameRef, data);
    }

    void writeByteArray(byte[] data) {
        dataStream.write(data, 0, data.length);
    }

    void writeStringOrEmpty(String string) {
        if (string == null) {
            string = "";
        }
        writeString(string);
    }

    void writeString(String string) {
        try {
            byte[] stringBytes = string.getBytes("UTF8");
            writeInt(stringBytes.length);
            writeByteArray(stringBytes);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new InternalException("Cannot convert string to UTF8 bytes");
        }
    }

    void writeLocation(LocationImpl location) {
        ReferenceTypeImpl refType = location.declaringType();
        byte tag;
        if (refType instanceof ClassTypeImpl) {
            tag = JDWP.TypeTag.CLASS;
        } else if (refType instanceof InterfaceTypeImpl) {
            // It's possible to have executable code in an interface
            tag = JDWP.TypeTag.INTERFACE;
        } else {
            throw new InternalException("Invalid Location");
        }
        writeByte(tag);
        writeClassRef(refType.uniqueID());
        writeMethodRef(location.method().uniqueID());
        writeLong(location.codeIndex());
    }
    //
    void writeValue(ValueImpl val) {
//        try {
            writeValueChecked(val);
//        } catch (InvalidTypeException exc) {  // should never happen
//            throw new RuntimeException(
//                    "Internal error: Invalid Tag/Type pair");
//        }
    }

    void writeValueChecked(ValueImpl val) {
        writeByte(ValueImpl.typeValueKey(val));
        writeUntaggedValue(val);
    }

    void writeUntaggedValue(ValueImpl val) {
        writeUntaggedValueChecked(val);
    }

    void writeUntaggedValueChecked(ValueImpl val) {
        if (val == null) {
            writeNullObjectRef();
        }
        else {
            val.writeUntaggedValue(this);
        }
    }


    /**
     * Read byte represented as one bytes.
     */
    byte readByte() {
        byte ret = pkt.data[inCursor];
        inCursor += 1;
        return ret;
    }

    /**
     * Read boolean represented as one byte.
     */
    boolean readBoolean() {
        byte ret = readByte();
        return (ret != 0);
    }

    /**
     * Read char represented as two bytes.
     */
    char readChar() {
        int b1, b2;

        b1 = pkt.data[inCursor++] & 0xff;
        b2 = pkt.data[inCursor++] & 0xff;

        return (char) ((b1 << 8) + b2);
    }

    /**
     * Read short represented as two bytes.
     */
    short readShort() {
        int b1, b2;

        b1 = pkt.data[inCursor++] & 0xff;
        b2 = pkt.data[inCursor++] & 0xff;

        return (short) ((b1 << 8) + b2);
    }

    /**
     * Read int represented as four bytes.
     */
    int readInt() {
        int b1, b2, b3, b4;

        b1 = pkt.data[inCursor++] & 0xff;
        b2 = pkt.data[inCursor++] & 0xff;
        b3 = pkt.data[inCursor++] & 0xff;
        b4 = pkt.data[inCursor++] & 0xff;

        return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
    }

    /**
     * Read long represented as eight bytes.
     */
    long readLong() {
        long b1, b2, b3, b4;
        long b5, b6, b7, b8;

        b1 = pkt.data[inCursor++] & 0xff;
        b2 = pkt.data[inCursor++] & 0xff;
        b3 = pkt.data[inCursor++] & 0xff;
        b4 = pkt.data[inCursor++] & 0xff;

        b5 = pkt.data[inCursor++] & 0xff;
        b6 = pkt.data[inCursor++] & 0xff;
        b7 = pkt.data[inCursor++] & 0xff;
        b8 = pkt.data[inCursor++] & 0xff;

        return ((b1 << 56) + (b2 << 48) + (b3 << 40) + (b4 << 32)
                + (b5 << 24) + (b6 << 16) + (b7 << 8) + b8);
    }

    /**
     * Read float represented as four bytes.
     */
    float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Read double represented as eight bytes.
     */
    double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Read string represented as four byte length followed by
     * characters of the string.
     */
    String readString() {
        String ret;
        int len = readInt();

        try {
            ret = new String(pkt.data, inCursor, len, "UTF8");
        } catch (java.io.UnsupportedEncodingException e) {
            System.err.println(e);
            ret = "Conversion error!";
        }
        inCursor += len;
        return ret;
    }

    private long readID(int size) {
        switch (size) {
            case 8:
                return readLong();
            case 4:
                return (long) readInt();
            case 2:
                return (long) readShort();
            default:
                throw new UnsupportedOperationException("JDWP: ID size not supported: " + size);
        }
    }

    /**
     * Read object represented as vm specific byte sequence.
     */
    long readObjectRef() {
        return readID(vm.sizeofObjectRef);
    }

    long readClassRef() {
        return readID(vm.sizeofClassRef);
    }

    void writeTaggedObjectReference(ObjectReferenceImpl ref) {
        writeByte(ValueImpl.typeValueKey(ref));
        writeUntaggedObjectReference(ref);
    }

    private void writeUntaggedObjectReference(ObjectReferenceImpl ref) {
        if (ref == null) {
            writeNullObjectRef();
        } else {
            writeObjectRef(ref.uniqueID());
        }
    }

//    ObjectReferenceImpl readTaggedObjectReference() {
//        byte typeKey = readByte();
//        return vm.objectMirror(readObjectRef(), typeKey);
//    }

    ObjectReferenceImpl readObjectReference() {
        return vm.vm.objectMirror(readObjectRef());
    }
    //
//    StringReferenceImpl readStringReference() {
//        long ref = readObjectRef();
//        return vm.stringMirror(ref);
//    }
//
    public ArrayReferenceImpl readArrayReference() {
        long ref = readObjectRef();
        return (ArrayReferenceImpl) vm.vm.objectMirror(ref);
    }
    //
    ThreadReferenceImpl readThreadReference() {
        return vm.vm.getThreadById(readObjectRef());
    }

    void writeThreadReference(ThreadReferenceImpl thread) {
        writeUntaggedObjectReference(thread);
    }
    //
    ThreadGroupReferenceImpl readThreadGroupReference() {
        long ref = readObjectRef();
        return vm.vm.getThreadGroupReferenceById(ref);
    }

    void writeThreadGroupReference(ThreadGroupReferenceImpl ref) {
        writeUntaggedObjectReference(ref);
    }
    //
    ClassLoaderReferenceImpl readClassLoaderReference() {
        long ref = readObjectRef();
        return (ClassLoaderReferenceImpl) vm.vm.objectMirror(ref);
    }

    ClassObjectReferenceImpl readClassObjectReference() {
        long ref = readObjectRef();
        return (ClassObjectReferenceImpl) vm.vm.objectMirror(ref);
    }

    ReferenceTypeImpl readReferenceType() {
//        byte tag = readByte();
        long ref = readObjectRef();
        return vm.vm.getReferenceTypeById(ref);
    }

    void writeClassLoaderReference(ClassLoaderReferenceImpl ref) {
        writeUntaggedObjectReference(ref);
    }

    void writeClassObjectReference(ClassObjectReferenceImpl ref) {
        writeObjectRef(ref.uniqueID());
    }

    /**
     * Read method reference represented as vm specific byte sequence.
     */
    long readMethodRef() {
        return readID(vm.sizeofMethodRef);
    }

    /**
     * Read field reference represented as vm specific byte sequence.
     */
    long readFieldRef() {
        return readID(vm.sizeofFieldRef);
    }
    //
//    /**
//     * Read field represented as vm specific byte sequence.
//     */
//    Field readField() {
//        ReferenceTypeImpl refType = readReferenceType();
//        long fieldRef = readFieldRef();
//        return refType.getFieldMirror(fieldRef);
//    }
//
//    /**
//     * Read frame represented as vm specific byte sequence.
//     */
    long readFrameRef() {
        return readID(vm.sizeofFrameRef);
    }

    //    /**
//     * Read a value, first byte describes type of value to read.
//     */
//    ValueImpl readValue() {
//        byte typeKey = readByte();
//        return readUntaggedValue(typeKey);
//    }
//
//    ValueImpl readUntaggedValue(byte typeKey) {
//        ValueImpl val = null;
//
//        if (isObjectTag(typeKey)) {
//            val = vm.objectMirror(readObjectRef(), typeKey);
//        } else {
//            switch(typeKey) {
//                case JDWP.Tag.BYTE:
//                    val = new ByteValueImpl(vm, readByte());
//                    break;
//
//                case JDWP.Tag.CHAR:
//                    val = new CharValueImpl(vm, readChar());
//                    break;
//
//                case JDWP.Tag.FLOAT:
//                    val = new FloatValueImpl(vm, readFloat());
//                    break;
//
//                case JDWP.Tag.DOUBLE:
//                    val = new DoubleValueImpl(vm, readDouble());
//                    break;
//
//                case JDWP.Tag.INT:
//                    val = new IntegerValueImpl(vm, readInt());
//                    break;
//
//                case JDWP.Tag.LONG:
//                    val = new LongValueImpl(vm, readLong());
//                    break;
//
//                case JDWP.Tag.SHORT:
//                    val = new ShortValueImpl(vm, readShort());
//                    break;
//
//                case JDWP.Tag.BOOLEAN:
//                    val = new BooleanValueImpl(vm, readBoolean());
//                    break;
//
//                case JDWP.Tag.VOID:
//                    val = new VoidValueImpl(vm);
//                    break;
//            }
//        }
//        return val;
//    }
//
//    /**
//     * Read location represented as vm specific byte sequence.
//     */
//    Location readLocation() {
//        byte tag = readByte();
//        long classRef = readObjectRef();
//        long methodRef = readMethodRef();
//        long codeIndex = readLong();
//        if (classRef != 0) {
//            /* Valid location */
//            ReferenceTypeImpl refType = vm.referenceType(classRef, tag);
//            return new LocationImpl(vm, refType, methodRef, codeIndex);
//        } else {
//            /* Null location (example: uncaught exception) */
//           return null;
//        }
//    }
//
    byte[] readByteArray(int length) {
        byte[] array = new byte[length];
        System.arraycopy(pkt.data, inCursor, array, 0, length);
        inCursor += length;
        return array;
    }
//
//    List<Value> readArrayRegion() {
//        byte typeKey = readByte();
//        int length = readInt();
//        List<Value> list = new ArrayList<Value>(length);
//        boolean gettingObjects = isObjectTag(typeKey);
//        for (int i = 0; i < length; i++) {
//            /*
//             * Each object comes back with a type key which might
//             * identify a more specific type than the type key we
//             * passed in, so we use it in the decodeValue call.
//             * (For primitives, we just use the original one)
//             */
//            if (gettingObjects) {
//                typeKey = readByte();
//            }
//            Value value = readUntaggedValue(typeKey);
//            list.add(value);
//        }
//
//        return list;
//    }

    void writeArrayRegion(List<ValueImpl> srcValues, byte typeTag) {
        writeByte(typeTag);
        writeInt(srcValues.size());
        boolean withTags = isObjectTag(typeTag);
        for (ValueImpl value : srcValues) {
            if (withTags) {
                writeValue(value);
            }
            else {
                writeUntaggedValue(value);
            }
        }
    }
    //
//    int skipBytes(int n) {
//        inCursor += n;
//        return n;
//    }
//
//    byte command() {
//        return (byte)pkt.cmd;
//    }
//
    static boolean isObjectTag(byte tag) {
        return (tag == JDWP.Tag.OBJECT) ||
                (tag == JDWP.Tag.ARRAY) ||
                (tag == JDWP.Tag.STRING) ||
                (tag == JDWP.Tag.THREAD) ||
                (tag == JDWP.Tag.THREAD_GROUP) ||
                (tag == JDWP.Tag.CLASS_LOADER) ||
                (tag == JDWP.Tag.CLASS_OBJECT);
    }
}

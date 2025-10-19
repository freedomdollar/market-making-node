package com.zanable.marketmaking.bot.tools;

public class HexStringHandler {

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    public static byte[] hexStringToByteArray(String s) {

        s = s.replace(" ", "");
        s = s.replace("\n", "");

        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            try {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
            } catch (StringIndexOutOfBoundsException e) {
                // NOP
            }
        }
        return data;
    }

}

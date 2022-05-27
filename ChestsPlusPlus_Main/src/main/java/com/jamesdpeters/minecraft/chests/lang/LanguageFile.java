package com.jamesdpeters.minecraft.chests.lang;

import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class LanguageFile extends Properties {

    List<String> additionalComments = new ArrayList<>();

    public void addComment(String comment) {
        this.additionalComments.add(comment);
    }

    @Override
    public synchronized Enumeration<Object> keys() {
        ArrayList<Object> result = Collections.list(super.keys());
        result.sort(Comparator.comparing(Object::toString));
        return Collections.enumeration(result);
    }

    public void store(File file) throws IOException {
        store0(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)), false);
    }

    public void storeGenerated(File file) throws IOException {
        store0(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)), true);
    }

    private void store0(BufferedWriter bw, boolean generated)
            throws IOException {
        if (generated) {
            writeComments(bw, " Chests++ Language File (Version " + BuildConstants.VERSION + ")");
            writeComments(bw,
                        "NOTE: This file gets replaced when the plugin launches! If you want to make modifications create a copy first!" + System.lineSeparator() +
                        "To create a new language file simply create a copy of this file and rename it to your desired choice for example 'en_US.properties'" + System.lineSeparator() +
                        "It should be located in the 'lang' folder" + System.lineSeparator() +
                        "Then in config.yml 'language-file: default' would be renamed to 'language-file: en_US'" + System.lineSeparator() +
                        "To help contribute to the plugin and provide new language files you can create a pull-request at https://github.com/JamesPeters98/ChestsPlusPlus or join our Discord https://discord.gg/YRs3mP5"
                        );

            for (String additionalComment : additionalComments) {
                writeComments(bw, additionalComment);
            }
        }

        synchronized (this) {
            for (Enumeration<?> e = keys(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                String val = (String) get(key);
                key = saveConvert(key, true, false);
                /* No need to escape embedded and trailing spaces for value, hence
                 * pass false to flag.
                 */
                val = saveConvert(val, false, false);
                bw.write(key + " = " + val);
                bw.newLine();
            }
        }
        bw.flush();
    }

    private static void writeComments(BufferedWriter bw, String comments)
            throws IOException {
        bw.write("#");
        int len = comments.length();
        int current = 0;
        int last = 0;
        char[] uu = new char[6];
        uu[0] = '\\';
        uu[1] = 'u';
        while (current < len) {
            char c = comments.charAt(current);
            if (c > '\u00ff' || c == '\n' || c == '\r') {
                if (last != current)
                    bw.write(comments.substring(last, current));
                if (c > '\u00ff') {
                    uu[2] = toHex((c >> 12) & 0xf);
                    uu[3] = toHex((c >> 8) & 0xf);
                    uu[4] = toHex((c >> 4) & 0xf);
                    uu[5] = toHex(c & 0xf);
                    bw.write(new String(uu));
                } else {
                    bw.newLine();
                    if (c == '\r' &&
                            current != len - 1 &&
                            comments.charAt(current + 1) == '\n') {
                        current++;
                    }
                    if (current == len - 1 ||
                            (comments.charAt(current + 1) != '#' &&
                                    comments.charAt(current + 1) != '!'))
                        bw.write("#");
                }
                last = current + 1;
            }
            current++;
        }
        if (last != current)
            bw.write(comments.substring(last, current));
        bw.newLine();
    }

    /*
     * Converts unicodes to encoded &#92;uxxxx and escapes
     * special characters with a preceding slash
     */
    private String saveConvert(String theString,
                               boolean escapeSpace,
                               boolean escapeUnicode) {
        int len = theString.length();
        int bufLen = len * 2;
        if (bufLen < 0) {
            bufLen = Integer.MAX_VALUE;
        }
        StringBuffer outBuffer = new StringBuffer(bufLen);

        for (int x = 0; x < len; x++) {
            char aChar = theString.charAt(x);
            // Handle common case first, selecting largest block that
            // avoids the specials below
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append('\\');
                    outBuffer.append('\\');
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch (aChar) {
                case ' ':
                    if (x == 0 || escapeSpace)
                        outBuffer.append('\\');
                    outBuffer.append(' ');
                    break;
                case '\t':
                    outBuffer.append('\\');
                    outBuffer.append('t');
                    break;
                case '\n':
                    outBuffer.append('\\');
                    outBuffer.append('n');
                    break;
                case '\r':
                    outBuffer.append('\\');
                    outBuffer.append('r');
                    break;
                case '\f':
                    outBuffer.append('\\');
                    outBuffer.append('f');
                    break;
                case '=': // Fall through
                case ':': // Fall through
                case '#': // Fall through
                case '!':
                    outBuffer.append('\\');
                    outBuffer.append(aChar);
                    break;
                default:
                    if (((aChar < 0x0020) || (aChar > 0x007e)) & escapeUnicode) {
                        outBuffer.append('\\');
                        outBuffer.append('u');
                        outBuffer.append(toHex((aChar >> 12) & 0xF));
                        outBuffer.append(toHex((aChar >> 8) & 0xF));
                        outBuffer.append(toHex((aChar >> 4) & 0xF));
                        outBuffer.append(toHex(aChar & 0xF));
                    } else {
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }

    /**
     * Convert a nibble to a hex character
     *
     * @param nibble the nibble to convert.
     */
    private static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }

    /**
     * A table of hex digits
     */
    private static final char[] hexDigit = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
}

package org.fogbowcloud.manager.core.federatednetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessUtil {

    public static String getOutput(Process p) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(
                p.getInputStream()));
        String out = new String();
        while (true) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            out += line;
        }
        return out;
    }

    public static String getError(Process p) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(
                p.getErrorStream()));
        String error = new String();
        while (true) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            error += line;
        }
        return error;
    }

}
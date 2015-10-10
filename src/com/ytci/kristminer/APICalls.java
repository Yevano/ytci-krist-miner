package com.ytci.kristminer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class APICalls {
    private static String syncNode = "http://krist.ceriat.net/?";

    private static String webGet(String page) {
        URL url;
        InputStream is = null;
        BufferedReader br;
        String line;
        StringBuilder sb = new StringBuilder();

        try {
            url = new URL(page);
            is = url.openStream();  // throws an IOException
            br = new BufferedReader(new InputStreamReader(is));

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException ioe) {
                return null;
            }
        }
        return sb.toString();
    }

    private static String getPage(String query) {
        return webGet(syncNode + query);
    }

    public static String getLastBlock() {
        return getPage("lastblock");
    }

    public static String getWork() {
        return getPage("getwork");
    }

    public static String getMoneySupply() {
        return getPage("getmoneysupply");
    }

    public static String getBlockValue() {
        return getPage("getbaseblockvalue") + ", +" + getPage("namebonus");
    }

    public static String getBalance(String address) {
        return getPage("getbalance=" + address);
    }

    public static void submitBlock(String address, String nonce) {
        getPage("submitblock&address=" + address + "&nonce=" + nonce);
        System.out.println("nonce: "+nonce);
    }

    public static void updateSyncNode() {
        syncNode = webGet("https://raw.githubusercontent.com/BTCTaras/kristwallet/master/staticapi/syncNode") + "?";
    }
}

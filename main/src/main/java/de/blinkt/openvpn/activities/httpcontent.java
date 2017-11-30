/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by admin on 2017/11/27.
 */

public class httpcontent {


    public String GET(String url, Boolean cookie) {
        InputStream inputStream;
        String contant = null;
        HttpURLConnection httpURLConnection = null;
        int code = 0;
        try {
            httpURLConnection = (HttpURLConnection) new URL(url).openConnection();
            httpURLConnection.setConnectTimeout(1000);
            httpURLConnection.setReadTimeout(1000);
            if (cookie == true) {
                httpURLConnection.setRequestProperty("Cookie", MainActivity.uid_token);
            }
            httpURLConnection.setRequestMethod("GET");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            code = httpURLConnection.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (code == 200) {
            try {
                inputStream = httpURLConnection.getInputStream();
                contant = readstream(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return contant;
        } else {
            return "ERROR";
        }
    }


    public String readstream(InputStream inputStream) {
        String str = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int len = 0;
        try {

            while ((len = inputStream.read(bytes)) != -1) {
                byteArrayOutputStream.write(bytes, 0, len);
            }
            inputStream.close();


        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            str=new String(byteArrayOutputStream.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return str;
    }


}

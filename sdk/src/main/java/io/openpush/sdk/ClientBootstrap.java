package io.openpush.sdk;

import android.content.Context;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

/** Background-only lookup of an app-scoped PUBLIC enrollment key. Never reads admin data. */
final class ClientBootstrap {
    static void ensure(Context c)throws Exception{
        if(!AutoSetup.setting(c,"openpush_client_key").isEmpty())return;
        String id=AutoSetup.setting(c,"openpush_app_id"),project=ClientSettings.project(c);
        JSONObject request=new JSONObject().put("appId",id).put("projectId",project).put("packageName",c.getPackageName());
        HttpURLConnection connection=(HttpURLConnection)new URL(AutoSetup.base(c)+"/api/sdk/bootstrap").openConnection();
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty("X-OpenPush-Client","dashboard");connection.setDoOutput(true);
            byte[] body=request.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
            try(java.io.OutputStream out=connection.getOutputStream()){out.write(body);}
            int status=connection.getResponseCode();
            if(status!=200){android.util.Log.w("OpenPushSetup","BOOTSTRAP_HTTP_"+status);throw new java.io.IOException("OpenPush bootstrap unavailable");}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(java.io.InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[1024];int count;
                while((count=in.read(buffer))!=-1){if(bytes.size()+count>8192)throw new java.io.IOException();bytes.write(buffer,0,count);}
            }
            accept(c,request,new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
        }finally{connection.disconnect();}
    }
    static void accept(Context c,JSONObject request,JSONObject response)throws Exception{
        for(String field:new String[]{"appId","projectId","packageName"})
            if(!request.getString(field).equals(response.optString(field)))throw new IllegalStateException("OpenPush bootstrap configuration mismatch.");
        String key=response.optString("clientKey");
        if(!key.matches("[a-f0-9]{64}"))throw new IllegalStateException("OpenPush bootstrap public key is invalid.");
        ClientSettings.cacheKey(c,request.getString("appId"),request.getString("projectId"),key);
    }
}

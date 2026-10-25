package io.openpush.sdk;

import android.content.Context;
import android.os.Looper;
import android.util.AtomicFile;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Isolated central Firebase identity. FCM always belongs to the host DEFAULT app.
 * Uses the official Auth/Firestore HTTPS APIs to retain API 21 and host Firebase versions.
 * No default FirebaseAuth login, Service Account, or server sending credential is used.
 */
final class CentralTransport {
    static boolean enabled(Context c){return !BuildConfig.CENTRAL_PROJECT_ID.isEmpty() && !BuildConfig.CENTRAL_APPLICATION_ID.isEmpty()
        && !BuildConfig.CENTRAL_API_KEY.isEmpty() && ClientSettings.resource(c,"openpush_server_url").isEmpty();}
    static String marker(){return "https://firestore.googleapis.com/v1/projects/"+BuildConfig.CENTRAL_PROJECT_ID+"/databases/(default)/documents";}
    static void foreground(Context c){ClientSettings.prefs(c).edit().putLong("lastForegroundAt",System.currentTimeMillis()).apply();}
    static FirebaseApp secondary(Context c){
        String name="OpenPushCentral";
        for(FirebaseApp app:FirebaseApp.getApps(c))if(name.equals(app.getName()))return app;
        return FirebaseApp.initializeApp(c,new FirebaseOptions.Builder().setProjectId(BuildConfig.CENTRAL_PROJECT_ID)
            .setApplicationId(BuildConfig.CENTRAL_APPLICATION_ID).setApiKey(BuildConfig.CENTRAL_API_KEY).build(),name);
    }
    static synchronized Client client(Context c){secondary(c);return new Client(c.getApplicationContext(),new HttpsWire(),marker(),BuildConfig.CENTRAL_API_KEY);}
    static boolean challenge(Context c,Map<String,String> data){
        if(!data.containsKey("op_enroll_v"))return false;
        if(!enabled(c) || !"1".equals(data.get("op_enroll_v")))return true;
        String appId=ClientSettings.value(c,"openpush_app_id"),nonce=data.get("op_enroll_nonce");
        if(!appId.equals(data.get("op_enroll_app")) || nonce==null || !nonce.matches("[A-Za-z0-9_-]{43}"))return true;
        try{
            JSONObject auth=readAuth(c);
            if(!appId.equals(auth.optString("appId")) || !auth.optString("uid").equals(data.get("op_enroll_uid")))return true;
            ClientSettings.prefs(c).edit().putString("centralProof",nonce).commit();
            RegistrationJob.schedule(c);
        }catch(Exception unavailable){android.util.Log.w("OpenPushCentral","CHALLENGE_DEFERRED");}
        return true;
    }
    static AtomicFile authFile(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"openpush-central-auth.json"));}
    static JSONObject readAuth(Context c)throws Exception{
        AtomicFile file=authFile(c);if(!file.getBaseFile().isFile())return new JSONObject();
        byte[] bytes=file.readFully();if(bytes.length>32768)throw new IOException();
        return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
    }
    static void saveAuth(Context c,JSONObject auth)throws Exception{
        AtomicFile file=authFile(c);FileOutputStream out=null;
        try{out=file.startWrite();out.write(auth.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    interface Wire{Reply call(String url,String method,String body,String bearer,String contentType)throws Exception;}
    static final class Reply{final int status;final JSONObject json;Reply(int s,JSONObject j){status=s;json=j;}}
    static final class HttpsWire implements Wire{
        public Reply call(String url,String method,String body,String bearer,String contentType)throws Exception{
            if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("Network work must run in a background job");
            URL endpoint=new URL(url);if(!"https".equals(endpoint.getProtocol()))throw new IOException("HTTPS required");
            HttpURLConnection connection=(HttpURLConnection)endpoint.openConnection();
            try{
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
                connection.setRequestMethod(method);connection.setRequestProperty("Content-Type",contentType);
                if(!bearer.isEmpty())connection.setRequestProperty("Authorization","Bearer "+bearer);
                if(body!=null){connection.setDoOutput(true);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
                int status=connection.getResponseCode();InputStream raw=status<400?connection.getInputStream():connection.getErrorStream();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                if(raw!=null)try(InputStream in=raw){byte[] buffer=new byte[2048];int count;while((count=in.read(buffer))!=-1){if(bytes.size()+count>65536)throw new IOException("Response too large");bytes.write(buffer,0,count);}}
                return new Reply(status,bytes.size()==0?new JSONObject():new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
            }finally{connection.disconnect();}
        }
    }
    static final class Client{
        final Context c;final Wire wire;final String base,key;
        JSONObject auth;
        Client(Context c,Wire wire,String base,String key){this.c=c;this.wire=wire;this.base=base;this.key=key;}
        private String appId(){return ClientSettings.value(c,"openpush_app_id");}
        private synchronized String identity()throws Exception{
            if(auth==null)auth=readAuth(c);
            if(!appId().equals(auth.optString("appId")) || !BuildConfig.CENTRAL_PROJECT_ID.equals(auth.optString("projectId")))auth=new JSONObject();
            if(auth.optLong("expiresAt")>System.currentTimeMillis()+60000 && !auth.optString("idToken").isEmpty())return auth.getString("idToken");
            boolean refresh=!auth.optString("refreshToken").isEmpty();
            Reply response=refresh?wire.call("https://securetoken.googleapis.com/v1/token?key="+key,"POST","grant_type=refresh_token&refresh_token="+URLEncoder.encode(auth.getString("refreshToken"),"UTF-8"),"","application/x-www-form-urlencoded")
                :wire.call("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="+key,"POST","{\"returnSecureToken\":true}","","application/json");
            if(response.status!=200)throw new IOException("CENTRAL_AUTH_HTTP_"+response.status);
            JSONObject r=response.json;
            String uid=r.getString(refresh?"user_id":"localId");
            if(!uid.matches("[A-Za-z0-9_-]{1,128}"))throw new IOException("Invalid central identity");
            auth=new JSONObject().put("appId",appId()).put("projectId",BuildConfig.CENTRAL_PROJECT_ID).put("uid",uid)
                .put("idToken",r.getString(refresh?"id_token":"idToken")).put("refreshToken",r.getString(refresh?"refresh_token":"refreshToken"))
                .put("expiresAt",System.currentTimeMillis()+Long.parseLong(r.getString(refresh?"expires_in":"expiresIn"))*1000);
            saveAuth(c,auth);return auth.getString("idToken");
        }
        private Reply call(String path,String method,JSONObject body)throws Exception{
            String token=identity();Reply r=wire.call(base+path,method,body==null?null:body.toString(),token,"application/json");
            if(r.status==401){auth.put("expiresAt",0);token=identity();r=wire.call(base+path,method,body==null?null:body.toString(),token,"application/json");}
            return r;
        }
        private String name(String path){return base.substring(base.indexOf("/projects/")+1)+path;}
        private JSONObject string(String v)throws Exception{return new JSONObject().put("stringValue",v);}
        private JSONObject time(String field)throws Exception{return new JSONObject().put("fieldPath",field).put("setToServerValue","REQUEST_TIME");}
        private Reply commit(JSONObject write)throws Exception{return call(":commit","POST",new JSONObject().put("writes",new JSONArray().put(write)));}
        void register(String token,boolean enabled)throws Exception{
            identity();String uid=auth.getString("uid");
            Reply profile=call("/openpushApps/"+appId(),"GET",null);
            if(profile.status!=200)throw new IOException("CENTRAL_PROFILE_HTTP_"+profile.status);
            JSONObject config=profile.json.getJSONObject("fields");
            if(!ClientSettings.project(c).equals(config.getJSONObject("projectId").getString("stringValue"))
                || !c.getPackageName().equals(config.getJSONObject("packageName").getString("stringValue")))throw new IOException("Central application configuration mismatch");
            long opened=ClientSettings.prefs(c).getLong("lastForegroundAt",0);
            String day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date(opened));
            String proof=ClientSettings.prefs(c).getString("centralProof","");
            String stamp=uid+":"+digest(token+":"+enabled+":"+day+":"+proof);
            if(stamp.equals(ClientSettings.prefs(c).getString("centralLastStamp","")))return;
            JSONObject fields=new JSONObject().put("schema",new JSONObject().put("integerValue","1"))
                .put("appId",string(appId())).put("packageName",string(c.getPackageName())).put("projectId",string(ClientSettings.project(c)))
                .put("token",string(token)).put("notificationsEnabled",new JSONObject().put("booleanValue",enabled))
                .put("lastOpenedAt",new JSONObject().put("integerValue",Long.toString(opened))).put("proofNonce",string(proof));
            JSONArray masks=new JSONArray();for(Iterator<String> i=fields.keys();i.hasNext();)masks.put(i.next());
            Reply reply=commit(new JSONObject().put("update",new JSONObject().put("name",name("/openpushEnrollment/"+uid)).put("fields",fields))
                .put("updateMask",new JSONObject().put("fieldPaths",masks)).put("updateTransforms",new JSONArray().put(time("updatedAt"))));
            if(reply.status!=200)throw new IOException("CENTRAL_REGISTRATION_HTTP_"+reply.status);
            ClientSettings.prefs(c).edit().putString("centralLastStamp",stamp).commit();
        }
        int receipt(JSONObject body)throws Exception{
            String message=body.optString("messageId"),event=body.optString("event");
            if(!ClientSettings.validId(message))return 400;
            identity();String uid=auth.getString("uid"),path="/openpushReceipts/"+uid+"_"+message;
            Reply receipt=call(path,"GET",null);if(receipt.status!=200)return receipt.status;
            JSONObject fields=receipt.json.getJSONObject("fields");
            if(!uid.equals(fields.getJSONObject("ownerUid").getString("stringValue"))
                || !appId().equals(fields.getJSONObject("appId").getString("stringValue")))return 403;
            String[] changed;
            switch(event){case "received":changed=new String[]{"receivedAt"};break;
                case "displayed":changed=new String[]{"receivedAt","displayedAt"};break;
                case "clicked":changed=new String[]{"receivedAt","displayedAt","clickedAt"};break;
                case "display_failed":changed=new String[]{"receivedAt","displayFailedAt"};break;
                case "notification_disabled":changed=new String[]{"receivedAt","permissionDisabledAt"};break;
                default:return 400;}
            JSONArray transforms=new JSONArray();
            for(String field:changed)if(!fields.has(field)||fields.getJSONObject(field).has("nullValue"))transforms.put(time(field));
            if(transforms.length()==0)return 200;
            transforms.put(time("updatedAt"));
            JSONObject write=new JSONObject().put("update",new JSONObject().put("name",name(path)).put("fields",new JSONObject().put("schema",new JSONObject().put("integerValue","1"))))
                .put("updateMask",new JSONObject().put("fieldPaths",new JSONArray().put("schema"))).put("updateTransforms",transforms)
                .put("currentDocument",new JSONObject().put("updateTime",receipt.json.getString("updateTime")));
            return commit(write).status;
        }
        private String digest(String value)throws Exception{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();
        }
    }
}

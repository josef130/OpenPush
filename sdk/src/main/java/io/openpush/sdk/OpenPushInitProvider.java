package io.openpush.sdk;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
/** Lower initOrder than FirebaseInitProvider (100), so Firebase configuration exists first. */
public final class OpenPushInitProvider extends ContentProvider {
 public boolean onCreate(){AutoSetup.get(getContext());return true;}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
 public String getType(Uri u){return null;} public Uri insert(Uri u,ContentValues v){return null;}
 public int delete(Uri u,String s,String[] a){return 0;} public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}

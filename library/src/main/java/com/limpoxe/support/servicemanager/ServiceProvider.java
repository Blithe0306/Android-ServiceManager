package com.limpoxe.support.servicemanager;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.limpoxe.support.servicemanager.compat.BundleCompat;
import com.limpoxe.support.servicemanager.local.LocalServiceManager;

import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;


/**
 * Created by cailiming on 16/3/11.
 *
 * 利用ContentProvider实现同步跨进程调用
 *
 */
public class ServiceProvider extends ContentProvider {

    public static final String REPORT_BINDER = "report_binder";
    public static final String PUBLISH_SERVICE = "publish_service";
    public static final String PUBLISH_SERVICE_BINDER = "publish_service_binder";
    public static final String CALL_SERVICE = "call_service";
    public static final String QUERY_SERVICE = "query_service";
    public static final String QUERY_SERVICE_RESULT_IS_IN_PROVIDIDER_PROCESS = "query_service_result_is_in_provider_process";
    public static final String QUERY_SERVICE_RESULT_BINDER = "query_service_result_binder";
    public static final String QUERY_SERVICE_RESULT_DESCRIPTOR = "query_service_result_desciptor";
    public static final String QUERY_INTERFACE = "query_interface";
    public static final String QUERY_INTERFACE_RESULT = "query_interface_result";

    private static Uri CONTENT_URI;

    public static Uri buildUri() {
        if (CONTENT_URI == null) {
            CONTENT_URI = Uri.parse("content://"+ ServiceManager.sApplication.getPackageName() + "/call");
        }
        return CONTENT_URI;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {

        if (Build.VERSION.SDK_INT >= 19) {
            Log.d("call", "callingPackage = " + getCallingPackage());
        }

        Log.d("call", "Thead : id = " + Thread.currentThread().getId()
                + ", name = " + Thread.currentThread().getName()
                + ", method = " + method
                + ", arg = " + arg);

        if (method.equals(REPORT_BINDER)) {
            final int pid = extras.getInt("pid");
            IBinder iBinder = BundleCompat.getBinder(extras, "binder");
            processBinder.put(pid, iBinder);
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        processBinder.remove(pid);
                        Iterator<Map.Entry<String, Recorder>> iterator = allServiceList.entrySet().iterator();
                        while(iterator.hasNext()) {
                            Map.Entry<String, Recorder> entry = iterator.next();
                            if (entry.getValue().pid.equals(pid)) {
                                iterator.remove();
                            }
                        }
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
                processBinder.remove(pid);
            }
        } else if (method.equals(PUBLISH_SERVICE)) {

            String serviceName = arg;
            int pid = extras.getInt("pid");
            String interfaceClass = extras.getString("interface");
            IBinder binder =  processBinder.get(pid);
            if (binder != null && binder.isBinderAlive()) {
                Recorder recorder = new Recorder();
                recorder.pid = pid;
                recorder.interfaceClass = interfaceClass;
                allServiceList.put(serviceName, recorder);
            } else {
                allServiceList.remove(pid);
            }

            return null;

        } else if (method.equals(CALL_SERVICE)) {

            return MethodRouter.routerToInstance(extras);

        } else if (method.equals(QUERY_INTERFACE)) {
            Bundle bundle = new Bundle();
            Recorder recorder = allServiceList.get(arg);
            if (recorder != null) {
                bundle.putString(QUERY_INTERFACE_RESULT, recorder.interfaceClass);
            }
            return bundle;
        } else if (method.equals(QUERY_SERVICE)) {
            String serviceName = arg;
            if (allServiceList.containsKey(serviceName)) {

                Object instance = LocalServiceManager.getService(serviceName);

                Bundle bundle = new Bundle();
                if (instance != null && !Proxy.isProxyClass(instance.getClass())) {
                    bundle.putBoolean(QUERY_SERVICE_RESULT_IS_IN_PROVIDIDER_PROCESS, true);
                    return bundle;
                } else {
                    Recorder recorder = allServiceList.get(serviceName);
                    if (recorder != null) {
                        IBinder iBinder = processBinder.get(recorder.pid);
                        if (iBinder != null && iBinder.isBinderAlive()) {
                            bundle.putBoolean(QUERY_SERVICE_RESULT_IS_IN_PROVIDIDER_PROCESS, false);
                            bundle.putString(QUERY_SERVICE_RESULT_DESCRIPTOR, ProcessBinder.class.getName() + "_" + recorder.pid);
                            BundleCompat.putBinder(bundle, QUERY_SERVICE_RESULT_BINDER, iBinder);
                            return bundle;
                        }
                    }
                    return null;
                }
            }

        }
        return null;
    }

    //服务名：进程ID
    private static Hashtable<String, Recorder> allServiceList = new Hashtable<>();
    //进程ID：进程Binder
    private static Hashtable<Integer, IBinder> processBinder = new Hashtable<>();

    public static class Recorder {
        public Integer pid;
        public String interfaceClass;
    }


    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        //doNothing
        return null;
    }

    @Override
    public String getType(Uri uri) {
        //doNothing
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        //doNothing
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        //doNothing
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        //doNothing
        return 0;
    }

}

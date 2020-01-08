package com.ipaloma.jxbpaydemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.Iterator;

public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";
    private Button mCheckoutBtn;
    private EditText mSandbox;

    private Activity mActivity = null;

    private int payment_requestcode = 666666;
    private Intent mLoadActivityIntent;
    private String mResultUrl;
    private RadioGroup mChannel;
    private EditText mBillNumber;
    private PaymentStatusTask mPaymentStatusTask;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Sandbox
        mSandbox = (EditText) findViewById(R.id.sandbox_value);
        // 订单编号
        mBillNumber = (EditText) findViewById(R.id.billnumber_value);
        //支付方式选择
        mChannel = (RadioGroup) findViewById(R.id.channels);

        // 支付按钮
        mCheckoutBtn = (Button) findViewById(R.id.checkout);

        mCheckoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String sandbox = mSandbox.getText().toString().trim();
                if(sandbox.trim().equals("")) {
                    Toast.makeText(mActivity, R.string.no_sandbox_specified, Toast.LENGTH_LONG).show();
                    return;
                }
                String billnumber = mBillNumber.getText().toString().trim();
                if(billnumber.trim().equals("")) {
                    Toast.makeText(mActivity, R.string.no_billnumber_specified, Toast.LENGTH_LONG).show();
                    return;
                }
                int checkedId = mChannel.getCheckedRadioButtonId();
                //mLoadActivityIntent = getPackageManager().getLaunchIntentForPackage("com.ipaloma.jxbpay");
                mLoadActivityIntent = new Intent();
                mLoadActivityIntent.setComponent(new ComponentName("com.ipaloma.jxbpay", "com.ipaloma.jxbpay.MainActivity"));

                mLoadActivityIntent.putExtra("amount", 0.01);				// 支付金额
                mLoadActivityIntent.putExtra("sandbox", sandbox);	// 注册商户的二级域名
                mLoadActivityIntent.putExtra("title", "经销宝收银台");	// 定义收银台界面的title
                mLoadActivityIntent.putExtra("billnumber", billnumber);	// 订单编号
                mLoadActivityIntent.putExtra("notifyurl", "http://xxx?orderid="+billnumber);	// 支付完成后，将会调用此url（http post）通知结果(json格式)
                mLoadActivityIntent.putExtra("env",  checkedId == R.id.dev ? "dev" : checkedId == R.id.demo ? "demo" : "");

                 startActivityForResult(mLoadActivityIntent, payment_requestcode);

                // TODO the application not installed, try to install it first
            }
        });
        mActivity = this;

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "---onActivityResult--- \n" + requestCode + " " + resultCode + " " + (data == null ? "data = null" : data.getExtras().toString()));

        if (requestCode == payment_requestcode && data == null)
            return;
        if (requestCode == payment_requestcode && data != null) {
            String message = data.getExtras().getString("message");//得到消息
            String resulturl = data.getExtras().getString("result_url");//得到结果查询url

            Log.d(TAG, "message: " + message + " resulturl : " + (resulturl == null ? "" : resulturl));

            if (BuildConfig.DEBUG)
                Toast.makeText(this, resultCode == RESULT_OK ? "启动支付成功" : ("启动支付失败\n" + message), Toast.LENGTH_LONG).show();

            // check the status of payment with resulturl
            if (resulturl != null && !resulturl.trim().equals("")) {
                if(mPaymentStatusTask != null
                        && (mPaymentStatusTask.getStatus() == AsyncTask.Status.RUNNING  || mPaymentStatusTask.getStatus() == AsyncTask.Status.PENDING))
                    mPaymentStatusTask.cancel(true);

                mPaymentStatusTask = new PaymentStatusTask(resulturl, 60 * 1000);
                mPaymentStatusTask.execute();
            }
        }
    }

    private class PaymentStatusTask extends AsyncTask<Void, Void, String> {

        private final String mUrl;
        private final long mTimeout;
        private PaymentStatusTask mInstance;

        public PaymentStatusTask(String url, long timeout ){
            mUrl = url;
            mInstance = this;
            mTimeout = timeout;
        }
        @Override
        protected void onPreExecute(){
            new CountDownTimer(mTimeout, mTimeout) {
                public void onTick(long millisUntilFinished) {
                    // You can monitor the progress here as well by changing the onTick() time
                }
                public void onFinish() {
                    // stop async task if not in progress
                    if (mInstance.getStatus() == AsyncTask.Status.RUNNING  || mInstance.getStatus() == AsyncTask.Status.PENDING) {
                        mInstance.cancel(true);
                        // Add any specific task you wish to do as your extended class variable works here as well.
                    }
                }
            }.start();
        }
        @Override
        protected String doInBackground(Void... voids) {
            JSONObject result = null;
            boolean  bContinue = true;
            String content = "";

            while(!isCancelled() && bContinue) {
                byte[] buf = httpGet(mUrl);
                if (buf == null || buf.length == 0)
                    return null;
                content = new String(buf);
                Log.d(TAG, content);

                try {
                    result = new JSONObject(content);

                    if(!result.optString("error","").equals(""))
                        return content;
                    result = result.optJSONObject("data");
                    String status = result.optString("sessionstatus", "");
                    bContinue = status.equals("等待");
                    if(bContinue)
                        Thread.sleep(5000);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

            }
            StringBuilder stringBuilder = new StringBuilder();
            for (Iterator<String> it = result.keys(); it.hasNext(); ) {
                String keyStr = it.next();
                Object value = result.opt(keyStr);
                stringBuilder.append(keyStr + " : " + (value == null ? "" : value.toString()) + "\n");
            }
            if (BuildConfig.DEBUG)
                Toast.makeText(mActivity, stringBuilder.toString(), Toast.LENGTH_LONG).show();
            return content;
        }
    }
    public static byte[] httpGet(String url) {
        if (url == null || url.length() == 0)
            return null;

        HttpClient httpClient = createHttpClient();
        HttpGet httpGet = new HttpGet(url);

        try {
            HttpResponse response;
            if ((response = httpClient.execute(httpGet)).getStatusLine().getStatusCode() != 200) {
                Log.e(TAG, "httpGet fail, status code = " + response.getStatusLine().getStatusCode());
                return null;
            }
            return EntityUtils.toByteArray(response.getEntity());

        } catch (Exception e) {
            Log.e(TAG, "httpGet exception, e = " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static HttpClient createHttpClient() {
        try {
            KeyStore keyStore;
            (keyStore = KeyStore.getInstance(KeyStore.getDefaultType())).load((InputStream) null, (char[]) null);
            //SocketFactory socketFactory4;
            //(socketFactory4 = new SocketFactory(keyStore)).setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            BasicHttpParams httpParams;
            HttpProtocolParams.setVersion(httpParams = new BasicHttpParams(), HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(httpParams, "UTF-8");
            SchemeRegistry registry;
            (registry = new SchemeRegistry()).register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            //registry.register(new Scheme("https", socketFactory4, 443));
            ThreadSafeClientConnManager var5 = new ThreadSafeClientConnManager(httpParams, registry);
            return new DefaultHttpClient(var5, httpParams);
        } catch (Exception var3) {
            return new DefaultHttpClient();
        }
    }
}

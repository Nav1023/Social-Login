package com.example.soc_macmini_15.facebooklogin;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.Call;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.linkedin.platform.APIHelper;
import com.linkedin.platform.LISessionManager;
import com.linkedin.platform.errors.LIApiError;
import com.linkedin.platform.errors.LIAuthError;
import com.linkedin.platform.listeners.ApiListener;
import com.linkedin.platform.listeners.ApiResponse;
import com.linkedin.platform.listeners.AuthListener;
import com.linkedin.platform.utils.Scope;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.MessageDigestSpi;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private CallbackManager callbackManager;
    private TextView txtEmail, txtBirthday, txtFriends;
    private ProgressDialog progressDialog;
    private ImageView imgAvatar, linkedInLogin;
    private LoginButton loginButton;
    private Button btnLogout;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            callbackManager.onActivityResult(requestCode, resultCode, data);
            LISessionManager.getInstance(getApplicationContext()).onActivityResult(this, requestCode, resultCode, data);
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Cancelled the login", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        callbackManager = CallbackManager.Factory.create();

        txtBirthday = findViewById(R.id.txtBirthday);
        txtEmail = findViewById(R.id.txtEmail);
        txtFriends = findViewById(R.id.txtFriends);
        imgAvatar = findViewById(R.id.avtar);
        loginButton = findViewById(R.id.btn_login);
        linkedInLogin = findViewById(R.id.linkedinLogin);
        btnLogout = findViewById(R.id.logout_linkedin);

        loginButton.setOnClickListener(this);
        linkedInLogin.setOnClickListener(this);
        btnLogout.setOnClickListener(this);


        if (AccessToken.getCurrentAccessToken() != null) {
            //just setting the values.
            txtEmail.setText(AccessToken.getCurrentAccessToken().getUserId());
        }
        /*
        if(AccessToken.getCurrentAccessToken().isExpired()){
            linkedInLogin.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.GONE);
            loginButton.setVisibility(View.VISIBLE);
            imgAvatar.setVisibility(View.GONE);
            txtEmail.setVisibility(View.GONE);
            txtBirthday.setVisibility(View.GONE);
            txtFriends.setVisibility(View.GONE);
        }
        */
        // printKeyHash();
    }


    //For Facebook Integration

    private void getData(JSONObject object) {

        try {
            URL profile_picture = new URL("https://graph.facebook.com/" + object.getString("id") + "/picture?width=200&height=200");

            Picasso.with(this).load(profile_picture.toString()).into(imgAvatar);

            txtEmail.setText(object.getString("email"));
            txtFriends.setText("Friends:" + object.getJSONObject("friends").getJSONObject("summary").getString("total_count"));
            txtBirthday.setText(object.getString("birthday"));

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void facebookLogin() {

        loginButton.setReadPermissions(Arrays.asList("public_profile", "email", "user_birthday", "user_friends"));

        try {

            loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult loginResult) {
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setMessage("Retrieving Data...");
                    progressDialog.show();

                    String accessToken = loginResult.getAccessToken().getToken();

                    GraphRequest request = GraphRequest.newMeRequest(loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                        @Override
                        public void onCompleted(JSONObject object, GraphResponse response) {

                            progressDialog.dismiss();

                            Log.d(TAG, "onCompleted: " + response.toString());
                            getData(object);

                            linkedInLogin.setVisibility(View.GONE);
                            btnLogout.setVisibility(View.GONE);
                            loginButton.setVisibility(View.VISIBLE);
                        }
                    });
                    //Request Graph API

                    Bundle parameters = new Bundle();
                    parameters.putString("fields", "id,email,birthday,friends");
                    request.setParameters(parameters);
                    request.executeAsync();

                }

                @Override
                public void onCancel() {

                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Denied the authentication", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(FacebookException error) {
                    Toast.makeText(MainActivity.this, "" + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //End of LinkedIn Integration


    //For Linkedin Intergration

    private void linkedinLogin() {
        LISessionManager.getInstance(getApplicationContext()).init(this, buildScope(), new AuthListener() {
            @Override
            public void onAuthSuccess() {
                linkedInLogin.setVisibility(View.GONE);
                btnLogout.setVisibility(View.VISIBLE);
                loginButton.setVisibility(View.GONE);
                fetchLinkedInInfo();
            }

            @Override
            public void onAuthError(LIAuthError error) {
                Log.e(TAG, "onAuthError: " + error.toString());
            }
        }, true);
    }

    //Building the list of member permissions  our LinkedIn Session requires
    private static Scope buildScope() {
        return Scope.build(Scope.R_BASICPROFILE, Scope.W_SHARE, Scope.R_EMAILADDRESS);
    }

    private void fetchLinkedInInfo() {
        String url = "https://api.linkedin.com/v1/people/~:(id,first-name,last-name,public-profile-url,picture-url,email-address,picture-urls::(original))";

        APIHelper apiHelper = APIHelper.getInstance(getApplicationContext());
        apiHelper.getRequest(this, url, new ApiListener() {
            @Override
            public void onApiSuccess(ApiResponse apiResponse) {
                // Success!
                try {
                    JSONObject jsonObject = apiResponse.getResponseDataAsJson();
                    String firstName = jsonObject.getString("firstName");
                    String lastName = jsonObject.getString("lastName");
                    String picutreUrl = jsonObject.getString("pictureUrl");
                    String emailAddress = jsonObject.getString("emailAddress");

                    Picasso.with(getApplicationContext()).load(picutreUrl).into(imgAvatar);

                    txtBirthday.setText(firstName + " " + lastName);
                    txtEmail.setText(emailAddress);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onApiError(LIApiError liApiError) {
                // Error making GET request!''
                Toast.makeText(MainActivity.this, "" + liApiError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleLogout() {
        LISessionManager.getInstance(getApplicationContext()).clearSession();
        linkedInLogin.setVisibility(View.VISIBLE);
        btnLogout.setVisibility(View.GONE);
        loginButton.setVisibility(View.VISIBLE);
        imgAvatar.setVisibility(View.INVISIBLE);
        txtEmail.setVisibility(View.INVISIBLE);
        txtBirthday.setVisibility(View.INVISIBLE);

    }

    //End of LinkedIn integration


    //Click Event Handling
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.linkedinLogin:
                linkedinLogin();
                break;
            case R.id.logout_linkedin:
                handleLogout();
                break;
            case R.id.btn_login:
                facebookLogin();
                break;
        }
    }

/*
    private void printKeyHash() {

        try{
            PackageInfo info=getPackageManager().getPackageInfo("com.example.soc_macmini_15.facebooklogin", PackageManager.GET_SIGNATURES);
            for (Signature signature:info.signatures){
                MessageDigest md=MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d(TAG, "printKeyHash: "+ Base64.encodeToString(md.digest(),Base64.DEFAULT));

            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
    */

}

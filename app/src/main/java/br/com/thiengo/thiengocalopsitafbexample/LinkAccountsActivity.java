package br.com.thiengo.thiengocalopsitafbexample;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.alorma.github.sdk.bean.dto.response.Token;
import com.alorma.github.sdk.services.login.RequestTokenClient;
import com.alorma.github.sdk.services.user.GetAuthUserClient;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GithubAuthProvider;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.TwitterAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;

import java.util.Arrays;

import br.com.thiengo.thiengocalopsitafbexample.domain.User;
import rx.Observer;


public class LinkAccountsActivity extends CommonActivity
        implements GoogleApiClient.OnConnectionFailedListener, DatabaseReference.CompletionListener {

    private static final int RC_SIGN_IN_GOOGLE = 7859;

    private FirebaseAuth mAuth;

    private User user;
    private CallbackManager callbackManager;
    private GoogleApiClient mGoogleApiClient;

    private TwitterAuthClient twitterAuthClient;
    private Dialog gitHubDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link_accounts);

        // FACEBOOK
        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                accessFacebookLoginData( loginResult.getAccessToken() );
            }

            @Override
            public void onCancel() {}

            @Override
            public void onError(FacebookException error) {
                showSnackbar( error.getMessage() );
            }
        });


        // GOOGLE SIGN IN
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("312301797686-1bkt0nbecnbctpfoflanjr3sp4fi0aec.apps.googleusercontent.com")
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();


        // TWITTER
        twitterAuthClient = new TwitterAuthClient();


        mAuth = FirebaseAuth.getInstance();
        initViews();
        initUser();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if( requestCode == RC_SIGN_IN_GOOGLE ){

            GoogleSignInResult googleSignInResult = Auth.GoogleSignInApi.getSignInResultFromIntent( data );
            GoogleSignInAccount account = googleSignInResult.getSignInAccount();

            if( account == null ){
                showSnackbar("Google login falhou, tente novamente");
                return;
            }

            accessGoogleLoginData( account.getIdToken() );
        }
        else{
            twitterAuthClient.onActivityResult( requestCode, resultCode, data );
            callbackManager.onActivityResult( requestCode, resultCode, data );
        }
    }



    private void accessEmailLoginData( String email, String password ){
        accessLoginData(
                "email",
                email,
                password
        );
    }

    private void accessFacebookLoginData(AccessToken accessToken){
        accessLoginData(
                "facebook",
                (accessToken != null ? accessToken.getToken() : null)
        );
    }

    private void accessGoogleLoginData(String accessToken){
        accessLoginData(
                "google",
                accessToken
        );
    }

    private void accessTwitterLoginData(String token, String secret, String id){
        accessLoginData(
                "twitter",
                token,
                secret
        );
    }

    private void accessGithubLoginData(String accessToken){
        accessLoginData(
                "github",
                accessToken
        );
    }

    private void accessLoginData(final String provider, String... tokens ){
        if( tokens != null
                && tokens.length > 0
                && tokens[0] != null ){

            AuthCredential credential = FacebookAuthProvider.getCredential( tokens[0]);
            credential = provider.equalsIgnoreCase("google") ? GoogleAuthProvider.getCredential( tokens[0], null) : credential;
            credential = provider.equalsIgnoreCase("twitter") ? TwitterAuthProvider.getCredential( tokens[0], tokens[1] ) : credential;
            credential = provider.equalsIgnoreCase("github") ? GithubAuthProvider.getCredential( tokens[0] ) : credential;
            credential = provider.equalsIgnoreCase("email") ? EmailAuthProvider.getCredential( tokens[0], tokens[1] ) : credential;

            mAuth
                .getCurrentUser()
                .linkWithCredential( credential )
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        closeProgressBar();
                        closeGitHubDialog();

                        if( !task.isSuccessful() ){
                            return;
                        }

                        initButtons();
                        showSnackbar("Conta provider "+provider+" vinculada com sucesso.");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        FirebaseCrash.report( e );

                        closeProgressBar();
                        closeGitHubDialog();

                        showSnackbar("Error: "+e.getMessage());
                    }
                });
        }
        else{
            mAuth.signOut();
        }
    }

    private void closeGitHubDialog(){
        if( gitHubDialog != null ){
            gitHubDialog.dismiss();
        }
    }




    protected void initViews(){
        email = (AutoCompleteTextView) findViewById(R.id.email);
        password = (EditText) findViewById(R.id.password);
        progressBar = (ProgressBar) findViewById(R.id.login_progress);

        initButtons();
    }

    private void initButtons(){

        setButtonLabel(
            R.id.email_sign_in_button,
            EmailAuthProvider.PROVIDER_ID,
            R.string.action_link,
            R.string.action_unlink,
            R.id.til_email,
            R.id.til_password
        );

        setButtonLabel(
            R.id.email_sign_in_facebook_button,
            FacebookAuthProvider.PROVIDER_ID,
            R.string.action_link_facebook,
            R.string.action_unlink_facebook
        );

        setButtonLabel(
            R.id.email_sign_in_google_button,
            GoogleAuthProvider.PROVIDER_ID,
            R.string.action_link_google,
            R.string.action_unlink_google
        );

        setButtonLabel(
            R.id.email_sign_in_twitter_button,
            TwitterAuthProvider.PROVIDER_ID,
            R.string.action_link_twitter,
            R.string.action_unlink_twitter
        );

        setButtonLabel(
            R.id.email_sign_in_github_button,
            GithubAuthProvider.PROVIDER_ID,
            R.string.action_link_github,
            R.string.action_unlink_github
        );
    }

    private void setButtonLabel(
            int buttonId,
            String providerId,
            int linkId,
            int unlinkId,
            int... fieldsIds ){

        if( isALinkedProvider( providerId ) ){

            ((Button) findViewById( buttonId )).setText( getString( unlinkId ) );
            showHideFields( false, fieldsIds );
        }
        else{
            ((Button) findViewById( buttonId )).setText( getString( linkId ) );
            showHideFields( true, fieldsIds );
        }
    }

    private void showHideFields( boolean status, int... ids ){
        for( int id : ids ){
            findViewById( id ).setVisibility( status ? View.VISIBLE : View.GONE );
        }
    }

    private boolean isALinkedProvider( String providerId ){

        for(UserInfo userInfo : mAuth.getCurrentUser().getProviderData() ){

            if( userInfo.getProviderId().equals( providerId ) ){
                return true;
            }
        }
        return false;
    }

    protected void initUser(){
        user = new User();
        user.setEmail( email.getText().toString() );
        user.setPassword( password.getText().toString() );
    }

    public void sendLoginData( View view ){
        if( isALinkedProvider( EmailAuthProvider.PROVIDER_ID ) ){
            unlinkProvider( EmailAuthProvider.PROVIDER_ID );
            return;
        }

        openProgressBar();
        initUser();
        accessEmailLoginData( user.getEmail(), user.getPassword() );
    }

    public void sendLoginFacebookData( View view ){

        if( isALinkedProvider( FacebookAuthProvider.PROVIDER_ID ) ){
            unlinkProvider( FacebookAuthProvider.PROVIDER_ID );
            return;
        }

        LoginManager
            .getInstance()
            .logInWithReadPermissions(
                    this,
                    Arrays.asList("public_profile", "user_friends", "email")
            );
    }

    public void sendLoginGoogleData( View view ){
        if( isALinkedProvider( GoogleAuthProvider.PROVIDER_ID ) ){
            unlinkProvider( GoogleAuthProvider.PROVIDER_ID );
            return;
        }

        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN_GOOGLE);
    }

    public void sendLoginTwitterData( View view ){

        if( isALinkedProvider( TwitterAuthProvider.PROVIDER_ID ) ){
            unlinkProvider( TwitterAuthProvider.PROVIDER_ID );
            return;
        }

        twitterAuthClient.authorize(
                this,
                new Callback<TwitterSession>() {
                    @Override
                    public void success(Result<TwitterSession> result) {

                        TwitterSession session = result.data;

                        accessTwitterLoginData(
                                session.getAuthToken().token,
                                session.getAuthToken().secret,
                                String.valueOf( session.getUserId() )
                        );
                    }
                    @Override
                    public void failure(TwitterException exception) {
                        FirebaseCrash.report( exception );
                        showSnackbar( exception.getMessage() );
                    }
                }
        );
    }

    public void sendLoginGithubData( View view ){

        if( isALinkedProvider( GithubAuthProvider.PROVIDER_ID ) ){
            unlinkProvider( GithubAuthProvider.PROVIDER_ID );
            return;
        }

        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority("github.com")
                .appendPath("login")
                .appendPath("oauth")
                .appendPath("authorize")
                .appendQueryParameter("client_id", getString(R.string.github_app_id))
                .appendQueryParameter("scope", "user,user:email")
                .build();

        WebView webView = new WebView(this);
        webView.loadUrl( uri.toString() );
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {

                Uri uri = Uri.parse(url);

                if( uri.getQueryParameter("code") != null
                        && uri.getScheme() != null
                        && uri.getScheme().equalsIgnoreCase("https") ){

                    requestGitHubUserAccessToken( uri.getQueryParameter("code") );
                }

                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Login GitHub");
        alert.setView(webView);
        gitHubDialog = alert.show();
    }

    private void requestGitHubUserAccessToken( String code ){
        RequestTokenClient requestTokenClient = new RequestTokenClient(
                code,
                getString(R.string.github_app_id),
                getString(R.string.github_app_secret),
                getString(R.string.github_app_url)
        );

        requestTokenClient
                .observable()
                .subscribe(new Observer<Token>() {
                    @Override
                    public void onCompleted() {}

                    @Override
                    public void onError(Throwable e) {
                        FirebaseCrash.report( e );
                        showSnackbar( e.getMessage() );
                    }

                    @Override
                    public void onNext(Token token) {
                        if( token.access_token != null ){
                            requestGitHubUserData( token.access_token );
                        }
                    }
                });
    }

    private void requestGitHubUserData( final String accessToken ){
        GetAuthUserClient getAuthUserClient = new GetAuthUserClient( accessToken );
        getAuthUserClient
                .observable()
                .subscribe(new Observer<com.alorma.github.sdk.bean.dto.response.User>() {
                    @Override
                    public void onCompleted() {}

                    @Override
                    public void onError(Throwable e) {
                        FirebaseCrash.report( e );
                    }

                    @Override
                    public void onNext(com.alorma.github.sdk.bean.dto.response.User user) {
                        LinkAccountsActivity.this.user.setName( user.name );
                        LinkAccountsActivity.this.user.setEmail( user.email );

                        accessGithubLoginData( accessToken );
                    }
                });

    }




    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        FirebaseCrash.report( new Exception( connectionResult.getErrorMessage() ) );
        showSnackbar( connectionResult.getErrorMessage() );
    }

    @Override
    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
        if( databaseError != null ){
            FirebaseCrash.report( databaseError.toException() );
        }

        mAuth.getCurrentUser().delete();
        mAuth.signOut();
        finish();
    }


    private void unlinkProvider(final String providerId ){

        mAuth
            .getCurrentUser()
            .unlink( providerId )
            .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {

                    if( !task.isSuccessful() ){
                        return;
                    }

                    initButtons();
                    showSnackbar("Conta provider "+providerId+" desvinculada com sucesso.");

                    if( isLastProvider( providerId ) ){
                        user.setId( mAuth.getCurrentUser().getUid() );
                        user.removeDB( LinkAccountsActivity.this );
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    FirebaseCrash.report( e );
                    showSnackbar("Error: "+e.getMessage());
                }
            });
    }


    private boolean isLastProvider( String providerId ){
        int size = mAuth.getCurrentUser().getProviders().size();
        return(
            size == 0
            || (size == 1 && providerId.equals(EmailAuthProvider.PROVIDER_ID) )
        );
    }
}
package com.example.chate.activities;



import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;


import com.example.chate.adapters.RecentConversationsAdapter;
import com.example.chate.databinding.ActivityMainBinding;
import com.example.chate.listeners.ConversionListener;
import com.example.chate.models.ChatMessage;
import com.example.chate.models.User;
import com.example.chate.utilities.Constants;
import com.example.chate.utilities.PrefenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;



import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends BaseActivity implements ConversionListener {

    private ActivityMainBinding binding;
    private PrefenceManager prefenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationsAdapter conversationsAdapter;
    private FirebaseFirestore database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding=ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefenceManager=new PrefenceManager(getApplicationContext());
        init();
        loadUserDetails();
        getToken();
        setListeners();
        listenConversations();
    }
    private void init(){
        conversations=new ArrayList<>();
        conversationsAdapter=new RecentConversationsAdapter(conversations,this);
        binding.conversationsRecyvlerView.setAdapter(conversationsAdapter);
        database=FirebaseFirestore.getInstance();
    }
    private void setListeners()
    {
        binding.imageSignOut.setOnClickListener(v -> signOut());
        binding.fabNewChat.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(),UsersActivity.class)));
    }
    private void loadUserDetails(){
        binding.textName.setText(prefenceManager.getString(Constants.KEY_NAME));
        byte [] bytes= Base64.decode(prefenceManager.getString(Constants.KEY_IMAGE),Base64.DEFAULT);
        Bitmap bitmap= BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);
    }
    private void showToast(String message)
    {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
    private void listenConversations(){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID,prefenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID,prefenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }
    private final EventListener<QuerySnapshot> eventListener = (value,error) -> {
      if(error !=null){
          return;
      }
      if(value!=null){
          for(DocumentChange documentChange:value.getDocumentChanges()){
              if(documentChange.getType() == DocumentChange.Type.ADDED){
                  String senderId=documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                  String receiverId=documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                  ChatMessage chatMessage=new ChatMessage();
                  chatMessage.senderId=senderId;
                  chatMessage.receiverId=receiverId;
                  if(prefenceManager.getString(Constants.KEY_USER_ID).equals(senderId)){
                      chatMessage.conversionImage=documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                      chatMessage.conversionName=documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                      chatMessage.conversionId=documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                  }
                  else{
                      chatMessage.conversionImage=documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                      chatMessage.conversionName=documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                      chatMessage.conversionId=documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                  }
                  chatMessage.message=documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                  chatMessage.dateObject=documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                  conversations.add(chatMessage);
              }
              else if(documentChange.getType() == DocumentChange.Type.MODIFIED){
                  for(int i = 0; i<conversations.size(); i++){
                      String senderId=documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                      String receiverId=documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                      if(conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)){
                          conversations.get(i).message= documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                          conversations.get(i).dateObject=documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                          break;
                      }
                  }
              }

          }
          Collections.sort(conversations,(obj1,obj2)-> obj2.dateObject.compareTo(obj1.dateObject));
          conversationsAdapter.notifyDataSetChanged();
          binding.conversationsRecyvlerView.smoothScrollToPosition(0);
          binding.conversationsRecyvlerView.setVisibility(View.VISIBLE);
          binding.progressBar.setVisibility(View.GONE);
      }
    };


    private void getToken(){
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);

    }
    private void updateToken (String token)
    {
        prefenceManager.putString(Constants.KEY_FCM_TOKEN,token);
        FirebaseFirestore database =FirebaseFirestore.getInstance();
        DocumentReference documentReference=
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        prefenceManager.getString(Constants.KEY_USER_ID)
                );
        documentReference.update(Constants.KEY_FCM_TOKEN,token)
                .addOnFailureListener(e -> showToast("Güncelleme Başarısız"));
    }
    private void signOut()
    {
        showToast("Çıkış Yapılıyor...");
        FirebaseFirestore database= FirebaseFirestore.getInstance();
        DocumentReference documentReference=
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        prefenceManager.getString(Constants.KEY_USER_ID)
                );
        HashMap<String,Object> updates= new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    prefenceManager.clear();
                    startActivity(new Intent(getApplicationContext(),SignInActivity.class));
                    finish();

                })
                .addOnFailureListener(e -> showToast("Çıkış Başarısız"));
    }

    @Override
    public void onConversionClicked(User user) {
        Intent intent=new Intent(getApplicationContext(),ChatActivity.class);
        intent.putExtra(Constants.KEY_USER,user);
        startActivity(intent);

    }
}
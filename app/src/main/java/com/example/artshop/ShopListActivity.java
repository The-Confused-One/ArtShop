package com.example.artshop;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class ShopListActivity extends AppCompatActivity {

    private static final String LOG_TAG = ShopListActivity.class.getName();
    private FirebaseUser user;
    private FirebaseAuth mAuth;
    private RecyclerView mRecyclerView;
    private ArrayList<ShoppingItem> mItemList;
    private ShoppingItemAdapter mAdapter;

    private FirebaseFirestore mFirestore;
    private CollectionReference mItems;
    private NotificationHandler mNotificationHandler;
    private AlarmManager mAlarmManager;

    private int gridNumber = 1;
    private int cartItems = 0;
    private boolean viewRow = true;

    private FrameLayout redCircle;
    private TextView contentTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_shop_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null){
            Log.d(LOG_TAG, "Authenticated user.");
        } else {
            Log.d(LOG_TAG, "Unauthenticated user.");
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }

        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, gridNumber));
        mItemList = new ArrayList<>();

        mAdapter = new ShoppingItemAdapter(this, mItemList);
        mRecyclerView.setAdapter(mAdapter);

        mFirestore = FirebaseFirestore.getInstance();
        mItems = mFirestore.collection("Items");

        queryData();

        mNotificationHandler = new NotificationHandler(this);
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

    }

    private void initializeData() {
        String[] itemsList = getResources().getStringArray(R.array.item_names);
        String[] itemsInfo = getResources().getStringArray(R.array.item_descriptions);
        String[] itemsPrice = getResources().getStringArray(R.array.item_prices);

        TypedArray itemsImageResource = getResources().obtainTypedArray(R.array.item_images);
        TypedArray itemsRate = getResources().obtainTypedArray(R.array.item_ratings);

        for (int i = 0; i < itemsList.length; i++) {
            ShoppingItem item = new ShoppingItem(
                    itemsList[i],
                    itemsInfo[i],
                    itemsPrice[i],
                    itemsRate.getFloat(i, 0),
                    itemsImageResource.getResourceId(i, 0),
                    0);

            mItems.add(item);
        }

        itemsImageResource.recycle();
    }

    private void queryData() {
        mItemList.clear();
        mItems.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                initializeData();
                queryData();
                return;
            }

            mItems.orderBy("name").limit(10).get().addOnSuccessListener(snapshot -> {
                for (QueryDocumentSnapshot document : snapshot) {
                    ShoppingItem item = document.toObject(ShoppingItem.class);
                    item.setId(document.getId());
                    mItemList.add(item);
                }
                mAdapter.notifyDataSetChanged();
            });
        });
    }

    public void deleteItem(ShoppingItem item){
        DocumentReference ref = mItems.document(item._getId());
        ref.delete().addOnSuccessListener(success -> {
            Log.d(LOG_TAG, "Item is successfully deleted from the collection: " + item._getId());
        }).addOnFailureListener(failure -> {
            Toast.makeText(this, "Item " + item._getId() + " cannot be deleted", Toast.LENGTH_LONG).show();
        });
        queryData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.shop_list_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.search_bar);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menuItem);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                Log.d(LOG_TAG, s);
                mAdapter.getFilter().filter(s);
                return false;
            }
        });

        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int itemId = item.getItemId();

        if (itemId == R.id.logout_button) {
            Log.d(LOG_TAG, "Logout clicked.");
            FirebaseAuth.getInstance().signOut();
            finish();
            return true;

        } else if (itemId == R.id.settings_button) {
            Log.d(LOG_TAG, "Settings clicked.");
            return true;

        } else if (itemId == R.id.cart) {
            Log.d(LOG_TAG, "Cart clicked.");
            return true;

        } else if (itemId == R.id.view_selector) {
            Log.d(LOG_TAG, "View selector clicked.");
            if(viewRow){
                changeSpanCount(item, R.drawable.icon_list, 2);
            } else {
                changeSpanCount(item, R.drawable.icon_grid, 1);
            }

        } else {
            return super.onOptionsItemSelected(item);
        }

        return super.onOptionsItemSelected(item);
    }

    private void changeSpanCount(MenuItem item, int drawableId, int spanCount) {
        viewRow = !viewRow;
        item.setIcon(drawableId);
        GridLayoutManager layoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        layoutManager.setSpanCount(spanCount);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem alertMenuItem = menu.findItem(R.id.cart);
        FrameLayout rootView = (FrameLayout) alertMenuItem.getActionView();

        redCircle = rootView.findViewById(R.id.view_alert_red_circle);
        contentTextView = rootView.findViewById(R.id.view_alert_count_textview);

        rootView.setOnClickListener(v -> onOptionsItemSelected(alertMenuItem));

        return super.onPrepareOptionsMenu(menu);
    }

    public void updateAlertIcon(ShoppingItem item){
        cartItems = (cartItems + 1);
        Log.d(LOG_TAG, "cartItems increased to " + cartItems);
        if(0 < cartItems){
            contentTextView.setText(String.valueOf(cartItems));
            redCircle.setVisibility(View.VISIBLE);
        } else {
            redCircle.setVisibility(View.GONE);
        }

        mItems.document(item._getId()).update("cartedCount", item.getCartCount() + 1).addOnFailureListener(failure -> {
            Toast.makeText(this, "Item " + item._getId() + " cannot be changed.", Toast.LENGTH_LONG).show();
        });

        if (cartItems == 10){
            mNotificationHandler.send("Wow, you have 10 items in your cart already.");
        }

        queryData();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // ha nem ures kosarral lepunk ki, egy nap mulva jon az emlekezteto
        if (cartItems > 0){
            setAlarmManager();
        }
    }

    private void setAlarmManager(){
        long repeatInterval = AlarmManager.INTERVAL_DAY;
        long triggerTime = SystemClock.elapsedRealtime() + repeatInterval;

        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, FLAG_IMMUTABLE);

        mAlarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                repeatInterval,
                pendingIntent
        );

    }

}
package com.example.vocabmaster;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.vocabmaster.databinding.ActivityMainBinding;
import com.example.vocabmaster.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private ActivityMainBinding binding;
    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState) ;

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.navView, navController);
            NavigationUI.setupWithNavController(binding.navSidebar, navController);
            
            binding.navView.getMenu().getItem(2).setEnabled(false);

            binding.fabCreate.setOnClickListener(v -> {
                navController.navigate(R.id.navigation_add_course);
            });

            handleIntent(getIntent());
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavContainer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.bottomMargin = insets.bottom;
            v.setLayoutParams(mlp);
            return WindowInsetsCompat.CONSUMED;
        });

        setupNavHeader();
        setupSidebarListeners();
        
        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                updateNavHeader();
                // Re-bind logout listener in case side nav view was recreated/re-laid out
                bindLogoutButton();
            }
        });
    }

    private void setupSidebarListeners() {
        binding.navSidebar.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_logout) {
                logout();
                return true;
            }
            
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            }
            return handled;
        });

        bindLogoutButton();
    }

    private void bindLogoutButton() {
        MenuItem logoutMenuItem = binding.navSidebar.getMenu().findItem(R.id.nav_logout);
        if (logoutMenuItem != null && logoutMenuItem.getActionView() != null) {
            View actionView = logoutMenuItem.getActionView();
            View innerBtn = actionView.findViewById(R.id.btn_logout_inner);
            if (innerBtn != null) {
                innerBtn.setOnClickListener(v -> logout());
            }
        }
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupNavHeader() {
        if (binding.navSidebar.getHeaderCount() > 0) {
            View headerView = binding.navSidebar.getHeaderView(0);
            headerView.setOnClickListener(v -> {
                binding.navView.setSelectedItemId(R.id.navigation_profile);
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
        updateNavHeader();
    }

    public void openDrawer() {
        if (binding.drawerLayout != null) {
            binding.drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void updateNavHeader() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (binding.navSidebar.getHeaderCount() == 0) return;
        
        View headerView = binding.navSidebar.getHeaderView(0);
        ImageView avatarImg = headerView.findViewById(R.id.nav_header_avatar);
        TextView nameText = headerView.findViewById(R.id.nav_header_name);
        TextView uidText = headerView.findViewById(R.id.nav_header_uid);

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String name = snapshot.getString("name");
                        String shortId = snapshot.getString("shortId");
                        String avatar = snapshot.getString("avatar");

                        if (nameText != null) nameText.setText(name != null ? name : "Người dùng");
                        if (uidText != null) uidText.setText("ID: " + (shortId != null ? shortId : "N/A"));
                        
                        int resId = R.drawable.bear;
                        if (avatar != null) {
                            for (int i = 0; i < avatarValues.length; i++) {
                                if (avatarValues[i].equals(avatar)) {
                                    resId = avatarResIds[i];
                                    break;
                                }
                            }
                        }
                        if (avatarImg != null) avatarImg.setImageResource(resId);
                    }
                });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            if ("vocabmaster".equals(data.getScheme()) && "payment-success".equals(data.getHost())) {
                String planName = data.getQueryParameter("plan");
                if (planName == null) planName = "Premium";
                
                String daysStr = data.getQueryParameter("days");
                int days = 30;
                try {
                    if (daysStr != null) days = Integer.parseInt(daysStr);
                } catch (NumberFormatException e) {
                    days = 30;
                }
                
                Bundle bundle = new Bundle();
                bundle.putString("plan_name", planName);
                bundle.putInt("days", days);
                
                if (navController != null) {
                    try {
                        navController.navigate(R.id.navigation_premium_success, bundle);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

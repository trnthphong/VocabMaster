package com.example.vocabmaster;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.vocabmaster.databinding.ActivityMainBinding;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import nl.dionsegijn.konfetti.core.Angle;
import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.Spread;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.core.models.Shape;
import nl.dionsegijn.konfetti.core.models.Size;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private KonfettiView konfettiView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive System UI
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        konfettiView = binding.konfettiView;

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.navView, navController);
        }

        // Fix: Adjust bottom margin based on system navigation bar height
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavContainer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            
            // Keep the 16dp margin and add the navigation bar height
            int margin16dp = (int) (16 * getResources().getDisplayMetrics().density);
            mlp.bottomMargin = insets.bottom + margin16dp;
            v.setLayoutParams(mlp);
            
            return WindowInsetsCompat.CONSUMED;
        });

        startStars();
    }

    private void startStars() {
        EmitterConfig emitterConfig = new Emitter(10, TimeUnit.SECONDS).perSecond(40);
        Party party = new PartyFactory(emitterConfig)
                .angle(Angle.BOTTOM)
                .spread(Spread.ROUND)
                .colors(Arrays.asList(Color.WHITE, 0xFFFFD700, 0xFFCCCCCC))
                .setSpeedBetween(0.1f, 0.4f)
                .position(new Position.Relative(0.5, -0.1))
                .shapes(Shape.Circle.INSTANCE)
                .sizes(new Size(5, 5, 0.95f))
                .timeToLive(10000L)
                .fadeOutEnabled(true)
                .build();

        konfettiView.start(party);
    }

    @Override
    protected void onDestroy() {
        if (konfettiView != null) {
            konfettiView.reset();
        }
        super.onDestroy();
    }
}
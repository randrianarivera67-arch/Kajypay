package mg.kajypay.gateway;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#F3F5F4"));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        int size = (int) (96 * getResources().getDisplayMetrics().density);
        root.addView(logo, new LinearLayout.LayoutParams(size, size));
        TextView titre = new TextView(this);
        titre.setText("KajyPay");
        titre.setTextSize(28);
        titre.setTextColor(Color.parseColor("#0E1A14"));
        titre.setGravity(Gravity.CENTER);
        titre.setPadding(0, 32, 0, 8);
        root.addView(titre);
        TextView version = new TextView(this);
        version.setText("Version " + BuildConfig.VERSION_NAME);
        version.setTextColor(Color.parseColor("#56655D"));
        version.setGravity(Gravity.CENTER);
        root.addView(version);
        setContentView(root);
    }
}

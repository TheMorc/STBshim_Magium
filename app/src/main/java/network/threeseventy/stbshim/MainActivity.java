package network.threeseventy.stbshim;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startService(new Intent(this, ResolverService.class));

        Intent intent = getPackageManager().getLaunchIntentForPackage("studio.scillarium.ottnavigator");

        if (intent != null) {
            startActivity(intent);
        }

        finish();
    }
}
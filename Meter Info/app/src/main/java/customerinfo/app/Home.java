package customerinfo.app;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class Home extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);
        
        Button lookupBtn = findViewById(R.id.lookupBtn);
        Button htmlBtn = findViewById(R.id.htmlBtn);
        
        lookupBtn.setOnClickListener(v -> {
            startActivity(new Intent(Home.this, MainActivity.class));
        });
        
        htmlBtn.setOnClickListener(v -> {
            startActivity(new Intent(Home.this, HtmlActivity.class));
        });
    }
}
package com.example.xshell;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.xshellcode.XShell;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Button testButton;
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        resultText = findViewById(R.id.result_text);
        testButton = findViewById(R.id.test_button);
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cmd = "ping -c 10 127.0.0.1";

                Map<String, String> envMap = new HashMap<>();
                envMap.put("TEST_ENV_VALUE", getFilesDir().getAbsolutePath());

                StringBuilder sb = new StringBuilder();
                XShell.Builder builder = new XShell.Builder();
                builder.setCommand(Arrays.asList(cmd.split(" ")));
                builder.setEnvironment(envMap);
                builder.setWorkingDirectory(null);
                builder.setTimeout(0);
                builder.setRedirectErrorStream(true);
                builder.setProcessStatusCallback(new XShell.SimpleProcessStatusCallback() {
                    @Override
                    public void onReadInputStream(String inputStr) {
                        sb.append(inputStr);
                        Handler handler = resultText.getHandler();
                        if (handler != null) {
                            handler.removeCallbacksAndMessages(null);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    resultText.setText(sb.toString());
                                }
                            });
                        }
                    }
                });
                XShell shell = builder.build();
                shell.start();
            }
        });
    }
}
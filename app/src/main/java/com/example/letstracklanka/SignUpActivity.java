package com.example.letstracklanka;

import android.content.Intent; // මේක අලුතින් එකතු වෙන්න ඕනේ
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // අපි හදපු ලස්සන Design එක ලෝඩ් කරන්නේ මෙතනින්
        setContentView(R.layout.activity_sign_up);

        // භාෂාව තෝරන Dropdown එකට භාෂා ටික දැමීම
        String[] languages = {"English (US)", "Sinhala (LK)", "Tamil (LK)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                languages
        );

        AutoCompleteTextView dropdownLanguage = findViewById(R.id.dropdownLanguage);
        dropdownLanguage.setAdapter(adapter);

        // Back බටන් එක එබුවම ආපහු කලින් පිටුවට යන්න
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Send Code බටන් එක එබුවම OTP පිටුවට යනවා
        findViewById(R.id.btnSendCode).setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, OtpVerificationActivity.class);
            startActivity(intent);
        }); // කලින් කේතයේ මේ වරහන් ටික තමයි අඩුවෙලා තිබුණේ
    }
}
package com.vpn.service.test;

import android.content.Intent;
import android.net.VpnService;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    public static final int PREPARE_VPN_REQUEST_CODE = 18;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent prepareIntent = VpnService.prepare(getApplicationContext());
                if (prepareIntent == null) {
                    Intent intent = new Intent(getApplicationContext(), TestVpnService.class);
                    intent.setAction(TestVpnService.START_ACTION);
                    startService(intent);
                } else {
                    startActivityForResult(prepareIntent, PREPARE_VPN_REQUEST_CODE);
                }
            }
        });

        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), TestVpnService.class);
                intent.setAction(TestVpnService.STOP_ACTION);
                startService(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PREPARE_VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent intent = new Intent(getApplicationContext(), TestVpnService.class);
            intent.setAction(TestVpnService.START_ACTION);
            startService(intent);
        }
    }
}

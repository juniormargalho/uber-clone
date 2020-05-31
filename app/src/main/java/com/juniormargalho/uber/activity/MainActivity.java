package com.juniormargalho.uber.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.juniormargalho.uber.R;
import com.juniormargalho.uber.helper.UsuarioFirebase;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();
    }

    public void abrirTelaLogin(View view){
        startActivity(new Intent(this, LoginActivity.class));
    }

    public void abrirTelaCadastro(View view){
        startActivity(new Intent(this, CadastroActivity.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        UsuarioFirebase.redirecionaUsuarioLogado(MainActivity.this);
    }
}

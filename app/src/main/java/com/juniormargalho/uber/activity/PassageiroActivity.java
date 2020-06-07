package com.juniormargalho.uber.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.juniormargalho.uber.R;
import com.juniormargalho.uber.config.ConfiguracaoFirebase;
import com.juniormargalho.uber.helper.UsuarioFirebase;
import com.juniormargalho.uber.model.Destino;
import com.juniormargalho.uber.model.Requisicao;
import com.juniormargalho.uber.model.Usuario;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PassageiroActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private FirebaseAuth autenticacao;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private EditText editDestino;
    private LatLng localPassageiro;
    private LinearLayout linearLayoutDestino;
    private Button buttonChamarUber;
    private boolean uberChamado = false;
    private DatabaseReference firebaseRef;
    private Requisicao requisicao;

    /*
     * Lat/lon destino:-23.556407, -46.662365 (Av. Paulista, 2439)
     * Lat/lon passageiro: -23.562791, -46.654668
     * Lat/lon Motorista (a caminho):
     *   inicial: -23.563196, -46.650607
     *   intermediaria: -23.564801, -46.652196
     *   final: -23.562801, -46.654660
     * */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passageiro);

        inicializarComponentes();

        verificaStatusRequisicao();
    }

    private void verificaStatusRequisicao(){
        Usuario usuarioLogado = UsuarioFirebase.getDadosUsuarioLogado();
        DatabaseReference requisicoes = firebaseRef.child("requisicoes");
        Query requisicaoPesquisa = requisicoes.orderByChild("passageiro/id").equalTo( usuarioLogado.getId() );
        requisicaoPesquisa.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Requisicao> lista = new ArrayList<>();
                for( DataSnapshot ds: dataSnapshot.getChildren() ){
                    lista.add( ds.getValue( Requisicao.class ) );
                }
                Collections.reverse(lista);

                if( lista!= null && lista.size()>0 ){
                    requisicao = lista.get(0);

                    switch (requisicao.getStatus()){
                        case Requisicao.STATUS_AGUARDANDO :
                            linearLayoutDestino.setVisibility( View.GONE );
                            buttonChamarUber.setText("Cancelar Uber");
                            uberChamado = true;
                            break;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        recuperarLocalizacaoUsuario();
    }

    public void chamarUber(View view){

        if(!uberChamado){
            String enderecoDestino = editDestino.getText().toString();

            if(!enderecoDestino.equals("") || enderecoDestino != null){
                Address addressDestino = recuperarEndereco(enderecoDestino);
                if(addressDestino != null){
                    final Destino destino = new Destino();
                    destino.setCidade(addressDestino.getAdminArea());
                    destino.setCep(addressDestino.getPostalCode());
                    destino.setBairro(addressDestino.getSubLocality());
                    destino.setRua(addressDestino.getThoroughfare());
                    destino.setNumero(addressDestino.getFeatureName());
                    destino.setLatitude(String.valueOf(addressDestino.getLatitude()));
                    destino.setLongitude(String.valueOf(addressDestino.getLongitude()));

                    StringBuilder mensagem = new StringBuilder();
                    mensagem.append("Cidade: " + destino.getCidade() + "\n");
                    mensagem.append("Rua: " + destino.getRua() + "\n");
                    mensagem.append("Bairro: " + destino.getBairro() + "\n");
                    mensagem.append("Número: " + destino.getNumero() + "\n");
                    mensagem.append("Cep: " + destino.getCep());

                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setTitle("Confirme seu endereço!")
                            .setMessage(mensagem)
                            .setPositiveButton("Confirmar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    salvarRequisicao(destino);
                                    uberChamado = true;
                                }
                            }).setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            }else {
                Toast.makeText(this, "Informe o endereço de destino!", Toast.LENGTH_SHORT).show();
            }
        }else {
            uberChamado = false;
        }
    }

    private void salvarRequisicao(Destino destino){
        Requisicao requisicao = new Requisicao();
        requisicao.setDestino( destino );

        Usuario usuarioPassageiro = UsuarioFirebase.getDadosUsuarioLogado();
        usuarioPassageiro.setLatitude( String.valueOf( localPassageiro.latitude ) );
        usuarioPassageiro.setLongitude( String.valueOf( localPassageiro.longitude ) );

        requisicao.setPassageiro( usuarioPassageiro );
        requisicao.setStatus( Requisicao.STATUS_AGUARDANDO );
        requisicao.salvar();

        linearLayoutDestino.setVisibility(View.GONE);
        buttonChamarUber.setText("Cancelar uber");
    }

    private Address recuperarEndereco(String endereco){
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> listaEnderecos = geocoder.getFromLocationName(endereco, 1);
            if(listaEnderecos != null && listaEnderecos.size() > 0){
                Address address = listaEnderecos.get(0);
                return address;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressLint("NewApi")
    private void recuperarLocalizacaoUsuario() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                localPassageiro = new LatLng(latitude, longitude);
                UsuarioFirebase.atualizarDadosLocalizacao(latitude, longitude);

                mMap.clear();
                mMap.addMarker(new MarkerOptions()
                        .position(localPassageiro)
                        .title("Meu local")
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.usuario)));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(localPassageiro, 20));
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menuSair :
                autenticacao.signOut();
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void inicializarComponentes(){
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Iniciar uma viagem");
        setSupportActionBar(toolbar);

        editDestino = findViewById(R.id.editDestino);
        linearLayoutDestino = findViewById(R.id.linearLayoutDestino);
        buttonChamarUber = findViewById(R.id.buttonChamarUber);

        autenticacao = ConfiguracaoFirebase.getFirebaseAutenticacao();
        firebaseRef = ConfiguracaoFirebase.getFirebaseDatabase();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }
}

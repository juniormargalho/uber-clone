package com.juniormargalho.uber.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.juniormargalho.uber.R;
import com.juniormargalho.uber.config.ConfiguracaoFirebase;
import com.juniormargalho.uber.helper.Local;
import com.juniormargalho.uber.helper.UsuarioFirebase;
import com.juniormargalho.uber.model.Destino;
import com.juniormargalho.uber.model.Requisicao;
import com.juniormargalho.uber.model.Usuario;

import java.text.DecimalFormat;

public class CorridaActivity extends AppCompatActivity implements OnMapReadyCallback {
    private Button buttonAceitarCorrida;
    private GoogleMap mMap;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private LatLng localMotorista, localPassageiro;
    private Usuario motorista, passageiro;
    private String idRequisicao, statusRequisicao;
    private Requisicao requisicao;
    private DatabaseReference firebaseRef;
    private Marker marcadorMotorista, marcadorPassageiro, marcadorDestino;
    private boolean requisicaoAtiva;
    private FloatingActionButton fabRota;
    private Destino destino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_corrida);

        inicializarComponentes();

        if( getIntent().getExtras().containsKey("idRequisicao") && getIntent().getExtras().containsKey("motorista") ){
            Bundle extras = getIntent().getExtras();
            motorista = (Usuario) extras.getSerializable("motorista");
            localMotorista = new LatLng(Double.parseDouble(motorista.getLatitude()), Double.parseDouble(motorista.getLongitude()));
            idRequisicao = extras.getString("idRequisicao");
            requisicaoAtiva = extras.getBoolean("requisicaoAtiva");

            verificaStatusRequisicao();
        }
    }

    private void verificaStatusRequisicao(){
        final DatabaseReference requisicoes = firebaseRef.child("requisicoes").child( idRequisicao );
        requisicoes.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                requisicao = dataSnapshot.getValue(Requisicao.class);

                if(requisicao != null){
                    passageiro = requisicao.getPassageiro();
                    localPassageiro = new LatLng(Double.parseDouble(passageiro.getLatitude()), Double.parseDouble(passageiro.getLongitude()));
                    statusRequisicao = requisicao.getStatus();
                    destino = requisicao.getDestino();
                    alteraInterfaceStatusRequisicao(statusRequisicao);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void requisicaoAguardando(){
        buttonAceitarCorrida.setText("Aceitar corrida");
        adicionaMarcadorMotorista(localMotorista, motorista.getNome() );
        centralizarMarcador(localMotorista);
    }

    private void requisicaoACaminho(){
        buttonAceitarCorrida.setText("A caminho do passageiro");
        fabRota.setVisibility(View.VISIBLE);

        //Exibe marcador do motorista
        adicionaMarcadorMotorista(localMotorista, motorista.getNome() );

        //Exibe marcador passageiro
        adicionaMarcadorPassageiro(localPassageiro, passageiro.getNome());

        //Centralizar dois marcadores
        centralizarDoisMarcadores(marcadorMotorista, marcadorPassageiro);

        //Inicia monitoramento do motorista / passageiro
        iniciarMonitoramento(motorista, localPassageiro, Requisicao.STATUS_VIAGEM);
    }

    private void requisicaoViagem(){
        fabRota.setVisibility(View.VISIBLE);
        buttonAceitarCorrida.setText("A caminho do destino");
        adicionaMarcadorMotorista(localMotorista, motorista.getNome());

        LatLng localDestino = new LatLng(
                Double.parseDouble(destino.getLatitude()),
                Double.parseDouble(destino.getLongitude()));

        adicionaMarcadorDestino(localDestino, "Destino");
        centralizarDoisMarcadores(marcadorMotorista, marcadorDestino);

        //Inicia monitoramento do motorista / destino do passageiro
        iniciarMonitoramento(motorista, localDestino, Requisicao.STATUS_FINALIZADA);
    }

    private void iniciarMonitoramento(final Usuario uOrigem, LatLng localDestino, final String status){
        DatabaseReference localUsuario = ConfiguracaoFirebase.getFirebaseDatabase().child("local_usuario");
        GeoFire geoFire = new GeoFire(localUsuario);

        //Adiciona círculo no passageiro
        final Circle circulo = mMap.addCircle(
                new CircleOptions()
                        .center( localDestino )
                        .radius(50)//em metros
                        .fillColor(Color.argb(90,255, 153,0))
                        .strokeColor(Color.argb(190,255,152,0)));

        final GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(localDestino.latitude, localDestino.longitude), 0.05);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if( key.equals(uOrigem.getId()) ){
                    requisicao.setStatus(status);
                    requisicao.atualizarStatus();
                    geoQuery.removeAllListeners();
                    circulo.remove();
                }
            }

            @Override
            public void onKeyExited(String key) {
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
            }

            @Override
            public void onGeoQueryReady() {
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
            }
        });
    }

    private void adicionaMarcadorMotorista(LatLng localizacao, String titulo){
        if( marcadorMotorista != null )
            marcadorMotorista.remove();

        marcadorMotorista = mMap.addMarker(new MarkerOptions()
                .position(localizacao)
                .title(titulo)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.carro)));
    }

    private void adicionaMarcadorPassageiro(LatLng localizacao, String titulo){
        if( marcadorPassageiro != null )
            marcadorPassageiro.remove();

        marcadorPassageiro = mMap.addMarker(new MarkerOptions()
                .position(localizacao)
                .title(titulo)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.usuario)));
    }

    private void centralizarDoisMarcadores(Marker marcador1, Marker marcador2){
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include( marcador1.getPosition() );
        builder.include( marcador2.getPosition() );
        LatLngBounds bounds = builder.build();

        int largura = getResources().getDisplayMetrics().widthPixels;
        int altura = getResources().getDisplayMetrics().heightPixels;
        int espacoInterno = (int) (largura * 0.20);
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,largura,altura,espacoInterno));
    }

    private void adicionaMarcadorDestino(LatLng localizacao, String titulo){
        if( marcadorPassageiro != null )
            marcadorPassageiro.remove();

        if( marcadorDestino != null )
            marcadorDestino.remove();

        marcadorDestino = mMap.addMarker(
                new MarkerOptions()
                        .position(localizacao)
                        .title(titulo)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.destino)));
    }

    public void aceitarCorrida(View view){
        requisicao = new Requisicao();
        requisicao.setId( idRequisicao );
        requisicao.setMotorista( motorista );
        requisicao.setStatus( Requisicao.STATUS_A_CAMINHO );
        requisicao.atualizar();
    }

    private void alteraInterfaceStatusRequisicao(String status){
        switch ( status ){
            case Requisicao.STATUS_AGUARDANDO :
                requisicaoAguardando();
                break;
            case Requisicao.STATUS_A_CAMINHO :
                requisicaoACaminho();
                break;
            case Requisicao.STATUS_VIAGEM :
                requisicaoViagem();
                break;
            case Requisicao.STATUS_FINALIZADA :
                requisicaoFinalizada();
                break;
        }
    }

    private void requisicaoFinalizada(){
        fabRota.setVisibility(View.GONE);
        requisicaoAtiva = false;

        if( marcadorMotorista != null )
            marcadorMotorista.remove();

        if( marcadorDestino != null )
            marcadorDestino.remove();

        //Exibe marcador de destino
        LatLng localDestino = new LatLng(
                Double.parseDouble(destino.getLatitude()),
                Double.parseDouble(destino.getLongitude())
        );
        adicionaMarcadorDestino(localDestino, "Destino");
        centralizarMarcador(localDestino);

        //Calcular distancia
        float distancia = Local.calcularDistancia(localPassageiro, localDestino);
        float valor = distancia * 8;
        DecimalFormat decimal = new DecimalFormat("0.00");
        String resultado = decimal.format(valor);

        buttonAceitarCorrida.setText("Corrida finalizada - R$ " + resultado );
    }

    private void centralizarMarcador(LatLng local){
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(local, 20));
    }

    @SuppressLint("NewApi")
    private void recuperarLocalizacaoUsuario() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                localMotorista = new LatLng(latitude, longitude);
                UsuarioFirebase.atualizarDadosLocalizacao(latitude, longitude);

                //Atualizar localização motorista no Firebase
                motorista.setLatitude(String.valueOf(latitude));
                motorista.setLongitude(String.valueOf(longitude));
                requisicao.setMotorista( motorista );
                requisicao.atualizarLocalizacaoMotorista();

                alteraInterfaceStatusRequisicao(statusRequisicao);
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
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        recuperarLocalizacaoUsuario();
    }

    private void inicializarComponentes(){
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Iniciar corrida");

        buttonAceitarCorrida = findViewById(R.id.buttonAceitarCorrida);
        firebaseRef = ConfiguracaoFirebase.getFirebaseDatabase();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Adiciona evento de clique no FabRota
        fabRota = findViewById(R.id.fabRota);
        fabRota.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String status = statusRequisicao;
                if( status != null && !status.isEmpty() ){
                    String lat = "";
                    String lon = "";

                    switch ( status ){
                        case Requisicao.STATUS_A_CAMINHO :
                            lat = String.valueOf(localPassageiro.latitude);
                            lon = String.valueOf(localPassageiro.longitude);
                            break;
                        case Requisicao.STATUS_VIAGEM :
                            lat = destino.getLatitude();
                            lon = destino.getLongitude();
                            break;
                    }

                    String latLong = lat + "," + lon;
                    Uri uri = Uri.parse("google.navigation:q="+latLong+"&mode=d");
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.setPackage("com.google.android.apps.maps");
                    startActivity(i);
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (requisicaoAtiva){
            Toast.makeText(CorridaActivity.this, "Necessário encerrar a requisição atual!", Toast.LENGTH_SHORT).show();
        }else {
            Intent i = new Intent(CorridaActivity.this, RequisicoesActivity.class);
            startActivity(i);
        }

        //Verificar o status da requisição para encerrar
        if( statusRequisicao != null && !statusRequisicao.isEmpty() ){
            requisicao.setStatus(Requisicao.STATUS_ENCERRADA);
            requisicao.atualizarStatus();
        }
        return false;
    }
}

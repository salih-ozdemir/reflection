Harika bir ekleme! gRPC'nin asenkron çalışma yeteneklerinden faydalanmak, özellikle yüksek performanslı ve tepkisel mikroservis mimarileri için kritik öneme sahip. Asenkron operasyonlar, IO blokajlarını ortadan kaldırarak uygulamanızın daha fazla isteği daha verimli bir şekilde işlemesini sağlar.

gRPC Asenkron Modelin Kütüphaneye Entegrasyonu
Şu anki GrpcManagedConnection'ımız ManagedChannel'ı yönetiyor ve bu kanal üzerinden hem blocking (senkron) hem de non-blocking (asenkron) stub'lar oluşturulabilir. Önemli olan, asenkron çalışma modelini kütüphanenin kendisinin nasıl desteklediği ve dışarıya nasıl açtığıdır.

1. gRPC Asenkron Stub ve Callbacks
   gRPC'nin asenkron modeli, Future veya StreamObserver tabanlı callback'ler ile çalışır.

FutureStub (Asenkron Tek Yönlü İletişim): Sunucudan tek bir yanıt beklediğiniz durumlar için kullanılır. RPC çağrısı bir ListenableFuture döndürür ve bu Future tamamlandığında sonuç elde edilir.

AsyncStub (StreamObserver Tabanlı Çift Yönlü İletişim): Sunucuya veya sunucudan akışlı veri gönderdiğiniz veya aldığınız senaryolar için kullanılır. StreamObserver arayüzü sayesinde veri parçalarını asenkron olarak işleyebilirsiniz.

2. GrpcManagedConnection'da Asenkron Stub Erişimi
   Mevcut GrpcManagedConnection sınıfınız, herhangi bir gRPC stub oluşturmak için genel bir getStub metodu sunuyor. Bu metod, asenkron stub'ları da kolayca almanızı sağlar.

Java

// GrpcManagedConnection sınıfı içinde (önceki haliyle aynı, değişiklik yok)

public <T extends AbstractStub<T>> T getStub(GrpcStubFactory<T> factory) {
if (channel == null) {
throw new IllegalStateException("gRPC channel is not connected. Call connect() first.");
}
// Burada, fabrika metodu zaten gRPC Channel'ı alıp istediğiniz stub tipini oluşturacak.
// Bu, Blocking, Future veya Async stub olabilir.
return factory.create(channel);
}
Bu getStub metodu esnek olduğu için, consuming uygulamanın hangi tür stub'ı isteyeceğine karar vermesine olanak tanır.

3. Asenkron Kullanım Örnekleri
   Uygulamanızda asenkron gRPC çağrıları yapmak için GrpcManagedConnection'ı nasıl kullanacağınızı görelim:

a. FutureStub (Tek Yönlü Asenkron)
Java

// MyUnifiedApplication içinde gRPC bağlantı yönetimi kısmı

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.stub.StreamObserver; // Gerekirse ekleyin
import java.util.concurrent.ExecutionException; // Future sonuçları için

// ... (Bağlantı yöneticisi ve diğer import'lar aynı kalır)

public class MyUnifiedApplication {
private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager();

    public static void main(String[] args) {
        List<ClientInterceptor> commonGrpcInterceptors = Arrays.asList(new CustomRetryInterceptor());

        try {
            GrpcManagedConnection grpcConn = connectionManager.getOrCreateConnection(
                "localhost:50051",
                id -> new GrpcManagedConnection(id, commonGrpcInterceptors)
            );

            // 1. FutureStub ile asenkron çağrı yapma
            // newFutureStub::new lambdası, GrpcManagedConnection'a FutureStub oluşturmasını söyler.
            MyServiceGrpc.MyServiceFutureStub futureClient = grpcConn.getStub(MyServiceGrpc.newFutureStub::new);

            System.out.println("Making asynchronous gRPC call (FutureStub)...");
            MyProto.MyRequest request = MyProto.MyRequest.newBuilder().setName("Asynchronous Future").build();
            ListenableFuture<MyProto.MyResponse> futureResponse = futureClient.myMethod(request);

            // Future'ın sonucunu bloklamadan, başka işler yapabiliriz.
            // Sonuç geldiğinde bir callback tetiklemek için addListener kullanırız.
            futureResponse.addListener(() -> {
                try {
                    MyProto.MyResponse response = futureResponse.get(); // Sonucu al
                    System.out.println("Asynchronous FutureStub Response: " + response.getMessage());
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("Asynchronous FutureStub call failed: " + e.getMessage());
                }
            }, MoreExecutors.directExecutor()); // Basit örnek için directExecutor, gerçekte bir ExecutorService kullanın

        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        } catch (RuntimeException | ConnectionException e) {
            System.err.println("Error with gRPC connection/call: " + e.getMessage());
        }

        // ... (Ethernet bağlantı yönetimi kısmı aynı kalır)
    }
}
Not: MoreExecutors.directExecutor() basit testler için uygun olsa da, production ortamlarında Future callback'leri için özel bir ExecutorService kullanmanız önerilir. Bu, uygulamanızın ana iş parçacığını (main thread) bloke etmemek ve daha iyi performans elde etmek içindir.

b. AsyncStub (StreamObserver Tabanlı Asenkron)
Bu model daha çok çift yönlü akışlar veya sunucu akışları için kullanılır.

Java

// MyUnifiedApplication içinde gRPC bağlantı yönetimi kısmı

// ... (Diğer import'lar ve kod aynı)

        try {
            GrpcManagedConnection grpcConn = connectionManager.getOrCreateConnection(
                "localhost:50051",
                id -> new GrpcManagedConnection(id, commonGrpcInterceptors)
            );

            // 2. AsyncStub ile asenkron çağrı yapma (StreamObserver tabanlı)
            // newStub::new lambdası, GrpcManagedConnection'a AsyncStub oluşturmasını söyler.
            MyServiceGrpc.MyServiceStub asyncClient = grpcConn.getStub(MyServiceGrpc.newStub::new);

            System.out.println("\nMaking asynchronous gRPC call (AsyncStub/StreamObserver)...");

            // RPC yanıtlarını işlemek için bir StreamObserver oluşturun
            StreamObserver<MyProto.MyResponse> responseObserver = new StreamObserver<MyProto.MyResponse>() {
                @Override
                public void onNext(MyProto.MyResponse response) {
                    System.out.println("Asynchronous StreamObserver Response: " + response.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Asynchronous StreamObserver call failed: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    System.out.println("Asynchronous StreamObserver call completed.");
                }
            };

            // Tek yönlü istek için (Unary RPC), istek gönder ve yanıt observer'ını ver
            MyProto.MyRequest request = MyProto.MyRequest.newBuilder().setName("Asynchronous Stream").build();
            asyncClient.myMethod(request, responseObserver);

            // Not: Sunucu akışı (Server-side streaming) veya çift yönlü akış (Bi-directional streaming)
            // senaryolarında, client tarafı StreamObserver'ı üzerinden istekler gönderir.
            // Örneğin, client-side streaming için:
            // StreamObserver<MyProto.MyRequest> requestObserver = asyncClient.clientStreamingMethod(responseObserver);
            // requestObserver.onNext(req1);
            // requestObserver.onNext(req2);
            // requestObserver.onCompleted();

        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
        } catch (RuntimeException | ConnectionException e) {
            System.err.println("Error with gRPC connection/call: " + e.getMessage());
        }
    }
}
Özet ve Entegrasyonun Avantajları
Temel Kütüphane Esnekliği: GrpcManagedConnection'ınız, altındaki ManagedChannel'ı yönettiği ve bir GrpcStubFactory kabul ettiği için, consuming uygulama istediği BlockingStub, FutureStub veya AsyncStub'ı oluşturabilir. Bu, kütüphanenizi asenkron veya senkron gRPC kullanımına zorlamaz, her iki modeli de destekler.

Asenkron Operasyonlar: FutureStub ile ListenableFuture kullanarak tekil RPC'leri asenkron olarak çalıştırabilir, yanıtı beklerken bloklama yapmadan başka işler yapabilirsiniz. AsyncStub ile StreamObserver kullanarak akış tabanlı asenkron iletişim kurabilirsiniz.

Performans: Asenkron model, ağ IO'sunu iş parçacığı blokajından ayırarak özellikle yüksek eşzamanlılık gerektiren mikroservisler için daha iyi performans ve kaynak kullanımı sağlar.

Bu yaklaşımla, ortak bağlantı yönetim kütüphaneniz gRPC'nin asenkron yeteneklerini tam olarak desteklerken, tüketici uygulamaların kendi ihtiyaçlarına göre senkron veya asenkron API'leri seçmelerine olanak tanır.



tümünü birleştirip tek örnekte verebilir misin


Harika! Şimdiye kadar konuştuğumuz tüm konuları tek bir kapsamlı örnekte birleştirelim. Bu örnekte, hem gRPC asenkron iletişimi hem de basit bir Ethernet bağlantısını yöneten ortak bir kütüphanenin nasıl kullanılacağını göreceğiz. Amacımız, uygulamanın düşük seviyeli bağlantı detaylarından soyutlanması ve her iki bağlantı türünün de otomatik yeniden bağlantı özelliklerine sahip olmasıdır.

Proje Yapısı (Maven ile Basit Bir Örnek)
Öncelikle, Maven tabanlı bir proje yapısı düşünelim.

my-universal-client/
├── pom.xml
└── src/
└── main/
├── java/
│   └── com/yourcompany/common/connection/
│       ├── ConnectionException.java
│       ├── GrpcManagedConnection.java
│       ├── GrpcStubFactory.java
│       ├── ManagedConnection.java
│       ├── UniversalConnectionManager.java
│       └── EthernetManagedConnection.java
│   └── com/yourcompany/myapp/
│       ├── MyUnifiedApplication.java
│       └── CustomRetryInterceptor.java (Önceki örneklerden)
└── proto/
└── my_service.proto (gRPC servis tanımı)
pom.xml
XML

<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
<modelVersion>4.0.0</modelVersion>

    <groupId>com.yourcompany</groupId>
    <artifactId>my-universal-client</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <grpc.version>1.64.0</grpc.version> <protobuf.version>3.25.1</protobuf.version>
        <protoc.version>3.25.1</protoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>33.1.0-jre</version>
        </dependency>

        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.13</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.13</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.1</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protoc.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
src/main/proto/my_service.proto
Protocol Buffers

syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.yourcompany.myapp.grpc";
option java_outer_classname = "MyServiceProto";

service MyService {
rpc MyMethod (MyRequest) returns (MyResponse);
}

message MyRequest {
string name = 1;
}

message MyResponse {
string message = 1;
}
Ortak Bağlantı Kütüphanesi (com.yourcompany.common.connection)
ConnectionException.java
Java

package com.yourcompany.common.connection;

public class ConnectionException extends Exception {
public ConnectionException(String message, Throwable cause) {
super(message, cause);
}
}
ManagedConnection.java
Java

package com.yourcompany.common.connection;

public interface ManagedConnection {
String getIdentifier();
boolean isConnected();
void connect() throws ConnectionException;
void disconnect();
}
GrpcStubFactory.java
Java

package com.yourcompany.common.connection;

import io.grpc.Channel;
import io.grpc.stub.AbstractStub;

@FunctionalInterface
public interface GrpcStubFactory<T extends AbstractStub<T>> {
T create(Channel channel);
}
GrpcManagedConnection.java
Java

package com.yourcompany.common.connection;

import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GrpcManagedConnection implements ManagedConnection {
private final String targetAddress;
private ManagedChannel channel;
private final List<ClientInterceptor> interceptors;

    public GrpcManagedConnection(String targetAddress, List<ClientInterceptor> interceptors) {
        this.targetAddress = targetAddress;
        this.interceptors = interceptors;
    }

    @Override
    public String getIdentifier() {
        return targetAddress;
    }

    @Override
    public boolean isConnected() {
        return channel != null && (channel.getState(true) == ConnectivityState.READY || channel.getState(true) == ConnectivityState.IDLE);
    }

    @Override
    public void connect() throws ConnectionException {
        if (channel == null || channel.isShutdown() || channel.isTerminated()) {
            System.out.println("Connecting gRPC to: " + targetAddress);
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(targetAddress)
                .usePlaintext(); // Production'da .useTransportSecurity() kullanın

            if (interceptors != null) {
                builder.intercept(interceptors);
            }

            channel = builder.build();
            // Kanalın bağlanmasını bir süre bekleyebiliriz (isteğe bağlı)
            // try {
            //     channel.awaitTermination(5, TimeUnit.SECONDS); // Bu, blocking bir çağrıdır
            // } catch (InterruptedException e) {
            //     Thread.currentThread().interrupt();
            //     throw new ConnectionException("gRPC initial connection interrupted", e);
            // }
        }
    }

    @Override
    public void disconnect() {
        if (channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            System.out.println("Disconnecting gRPC from: " + targetAddress);
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("gRPC channel shutdown interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                channel = null;
            }
        }
    }

    public <T extends AbstractStub<T>> T getStub(GrpcStubFactory<T> factory) {
        if (channel == null) {
            throw new IllegalStateException("gRPC channel is not connected. Call connect() first.");
        }
        return factory.create(channel);
    }
}
EthernetManagedConnection.java
Java

package com.yourcompany.common.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EthernetManagedConnection implements ManagedConnection {
private final String host;
private final int port;
private Socket socket;
private final ScheduledExecutorService healthCheckScheduler = Executors.newSingleThreadScheduledExecutor();

    public EthernetManagedConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String getIdentifier() {
        return host + ":" + port;
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void connect() throws ConnectionException {
        if (!isConnected()) {
            System.out.println("Connecting Ethernet to: " + host + ":" + port);
            try {
                // Bağlantı zaman aşımı ekleyelim
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000); // 5 saniye zaman aşımı
                System.out.println("Ethernet connected to: " + host + ":" + port);
            } catch (IOException e) {
                throw new ConnectionException("Failed to connect Ethernet to " + getIdentifier(), e);
            }
        }
    }

    @Override
    public void disconnect() {
        if (socket != null && !socket.isClosed()) {
            System.out.println("Disconnecting Ethernet from: " + host + ":" + port);
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing Ethernet socket: " + e.getMessage());
            } finally {
                socket = null;
            }
        }
    }

    // Harici UniversalConnectionManager tarafından çağrılacak bir health check mekanizması
    // Bu sınıfın kendi içinde periyodik kontrol yapması yerine, manager tarafından yönetilmesi daha uygun.
    // Ancak daha düşük seviyeli IO hatalarını yakalamak için burada da bir monitor thread olabilir.
    // Şimdilik manager'ın periyodik isConnected() kontrolüne güvenelim.

    public Socket getSocket() {
        return socket;
    }
}
UniversalConnectionManager.java
Java

package com.yourcompany.common.connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniversalConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(UniversalConnectionManager.class);

    private final Map<String, ManagedConnection> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true); // JVM çıkışında thread'in otomatik kapanmasını sağlar
        t.setName("connection-reconnect-scheduler");
        return t;
    });

    public UniversalConnectionManager() {
        // Arka planda periyodik olarak bağlantı durumunu kontrol et ve yeniden bağlan
        reconnectionScheduler.scheduleWithFixedDelay(() -> {
            activeConnections.forEach((id, conn) -> {
                if (!conn.isConnected()) {
                    logger.warn("Connection '{}' detected as disconnected. Attempting to reconnect...", id);
                    try {
                        conn.connect(); // Yeniden bağlanma denemesi
                        if (conn.isConnected()) {
                            logger.info("Successfully reconnected to '{}'.", id);
                        }
                    } catch (ConnectionException e) {
                        logger.error("Failed to reconnect '{}': {}", id, e.getMessage());
                        // Burada daha sofistike retry politikaları (örn: exponential backoff) uygulanabilir.
                    }
                }
            });
        }, 5, 10, TimeUnit.SECONDS); // 5 saniye sonra başla, her 10 saniyede bir kontrol et

        // JVM kapatıldığında tüm bağlantıları düzgünce kapatmak için shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAllConnections));
    }

    // Generic factory interface for creating different connection types
    @FunctionalInterface
    public interface ConnectionFactory<T extends ManagedConnection> {
        T create(String identifier);
    }

    public <T extends ManagedConnection> T getOrCreateConnection(String identifier, ConnectionFactory<T> factory) throws ConnectionException {
        // ComputeIfAbsent, thread-safe lazy initialization için kullanılır
        return (T) activeConnections.computeIfAbsent(identifier, id -> {
            logger.info("Creating new connection for: {}", id);
            T newConnection = factory.create(id);
            try {
                newConnection.connect(); // İlk bağlantı denemesi
            } catch (ConnectionException e) {
                logger.error("Initial connection failed for '{}': {}", id, e.getMessage());
                // İlk bağlantı başarısız olursa RuntimeException fırlatırız
                throw new RuntimeException(e);
            }
            return newConnection;
        });
    }

    public void shutdownAllConnections() {
        logger.info("Shutting down all managed connections...");
        reconnectionScheduler.shutdownNow(); // Yeniden bağlantı planlayıcısını durdur
        activeConnections.values().forEach(ManagedConnection::disconnect);
        activeConnections.clear();
        logger.info("All managed connections shut down.");
    }

    public <T extends ManagedConnection> T getConnection(String identifier, Class<T> connectionType) {
        ManagedConnection conn = activeConnections.get(identifier);
        if (conn != null && connectionType.isInstance(conn)) {
            return connectionType.cast(conn);
        }
        return null;
    }
}
Uygulama Kodu (com.yourcompany.myapp)
CustomRetryInterceptor.java
Java

package com.yourcompany.myapp; // Uygulama paketi içinde veya ortak kütüphanede olabilir

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class CustomRetryInterceptor implements ClientInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CustomRetryInterceptor.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 saniye

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            private int retries = 0;
            private long currentDelay = INITIAL_RETRY_DELAY_MS;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        // UNKNOWN (bağlantı henüz kurulamadı), UNAVAILABLE (servis kapalı/ulaşılamaz),
                        // DEADLINE_EXCEEDED (zaman aşımı) gibi durumları yakalayabiliriz.
                        if ((status.getCode() == Status.Code.UNAVAILABLE ||
                             status.getCode() == Status.Code.UNKNOWN ||
                             status.getCode() == Status.Code.DEADLINE_EXCEEDED) && retries < MAX_RETRIES) {
                            
                            retries++;
                            logger.warn("RPC call for {} failed with status {}. Retrying {}/{} in {}ms.",
                                    method.getFullMethodName(), status.getCode(), retries, MAX_RETRIES, currentDelay);
                            
                            try {
                                TimeUnit.MILLISECONDS.sleep(currentDelay);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                super.onClose(Status.CANCELLED.withDescription("Retry interrupted"), trailers);
                                return;
                            }
                            
                            // Exponential backoff
                            currentDelay *= 2; 

                            // Yeniden çağrıyı başlat
                            next.newCall(method, callOptions).start(this, headers);
                            return; // Hatayı henüz yukarıya iletme
                        }
                        super.onClose(status, trailers); // Diğer hataları veya retry limitini aşan hataları ilet
                    }
                }, headers);
            }
        };
    }
}
MyUnifiedApplication.java
Java

package com.yourcompany.myapp;

import com.yourcompany.common.connection.ConnectionException;
import com.yourcompany.common.connection.EthernetManagedConnection;
import com.yourcompany.common.connection.GrpcManagedConnection;
import com.yourcompany.common.connection.UniversalConnectionManager;
import com.yourcompany.myapp.grpc.MyServiceGrpc;
import com.yourcompany.myapp.grpc.MyServiceProto;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ClientInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MyUnifiedApplication {
private static final Logger logger = LoggerFactory.getLogger(MyUnifiedApplication.class);
private static final UniversalConnectionManager connectionManager = new UniversalConnectionManager();
// Asenkron gRPC callback'leri için özel bir ExecutorService
private static final ExecutorService grpcCallbackExecutor = Executors.newFixedThreadPool(5);


    public static void main(String[] args) throws InterruptedException {
        // Ortak gRPC interceptor'ları tanımla (örneğin, özel retry interceptor'ımız)
        List<ClientInterceptor> commonGrpcInterceptors = Arrays.asList(new CustomRetryInterceptor());

        // --- gRPC Bağlantı Yönetimi ---
        try {
            // gRPC bağlantısını al veya oluştur
            GrpcManagedConnection grpcConn = connectionManager.getOrCreateConnection(
                "localhost:50051", // Hedef gRPC servis adresi
                id -> new GrpcManagedConnection(id, commonGrpcInterceptors)
            );

            // Asenkron gRPC çağrısı (FutureStub kullanarak)
            MyServiceGrpc.MyServiceFutureStub futureClient = grpcConn.getStub(MyServiceGrpc.newFutureStub::new);
            logger.info("Asenkron gRPC çağrısı (FutureStub) başlatılıyor...");
            ListenableFuture<MyServiceProto.MyResponse> futureResponse = futureClient.myMethod(
                MyServiceProto.MyRequest.newBuilder().setName("Async gRPC Request").build()
            );

            // Future tamamlandığında çalışacak callback'i ekle
            Futures.addCallback(futureResponse, new FutureCallback<MyServiceProto.MyResponse>() {
                @Override
                public void onSuccess(MyServiceProto.MyResponse result) {
                    logger.info("Asenkron gRPC (FutureStub) yanıtı: {}", result.getMessage());
                }

                @Override
                public void onFailure(Throwable t) {
                    logger.error("Asenkron gRPC (FutureStub) çağrısı başarısız oldu: {}", t.getMessage(), t);
                }
            }, grpcCallbackExecutor); // Callback'in hangi thread pool'unda çalışacağını belirt

            // Asenkron gRPC çağrısı (AsyncStub/StreamObserver kullanarak)
            MyServiceGrpc.MyServiceStub asyncClient = grpcConn.getStub(MyServiceGrpc.newStub::new);
            logger.info("Asenkron gRPC çağrısı (AsyncStub/StreamObserver) başlatılıyor...");

            StreamObserver<MyServiceProto.MyResponse> responseObserver = new StreamObserver<MyServiceProto.MyResponse>() {
                @Override
                public void onNext(MyServiceProto.MyResponse response) {
                    logger.info("Asenkron gRPC (StreamObserver) yanıtı: {}", response.getMessage());
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("Asenkron gRPC (StreamObserver) çağrısı başarısız oldu: {}", t.getMessage(), t);
                }

                @Override
                public void onCompleted() {
                    logger.info("Asenkron gRPC (StreamObserver) çağrısı tamamlandı.");
                }
            };

            asyncClient.myMethod(MyServiceProto.MyRequest.newBuilder().setName("StreamObserver gRPC Request").build(), responseObserver);

        } catch (RuntimeException | ConnectionException e) {
            logger.error("gRPC bağlantısı veya çağrısı sırasında hata oluştu: {}", e.getMessage(), e);
        }

        // --- Ethernet Bağlantı Yönetimi ---
        try {
            // Ethernet bağlantısını al veya oluştur
            EthernetManagedConnection ethConn = connectionManager.getOrCreateConnection(
                "localhost:12345", // Hedef Ethernet cihaz adresi (host:port)
                id -> {
                    String[] parts = id.split(":");
                    return new EthernetManagedConnection(parts[0], Integer.parseInt(parts[1]));
                }
            );

            // Ethernet bağlantısını kullanarak veri gönderimi (basit örnek)
            if (ethConn.isConnected()) {
                logger.info("Ethernet bağlantısı etkin. Socket lokal portu: {}", ethConn.getSocket().getLocalPort());
                try (OutputStream os = ethConn.getSocket().getOutputStream()) {
                    String dataToSend = "Hello Ethernet Device!";
                    os.write(dataToSend.getBytes(StandardCharsets.UTF_8));
                    logger.info("Ethernet üzerinden veri gönderildi: '{}'", dataToSend);
                } catch (Exception e) {
                    logger.error("Ethernet üzerinden veri gönderilirken hata: {}", e.getMessage(), e);
                }
            } else {
                logger.warn("Ethernet bağlantısı aktif değil, veri gönderilemedi.");
            }

        } catch (RuntimeException | ConnectionException e) {
            logger.error("Ethernet bağlantısı sırasında hata oluştu: {}", e.getMessage(), e);
        }

        // Uygulamanın hemen kapanmaması için bir süre bekle (asenkron çağrıların tamamlanması için)
        TimeUnit.SECONDS.sleep(10);
        
        // Uygulama kapanırken executor'ları düzgünce kapat
        grpcCallbackExecutor.shutdown();
        try {
            if (!grpcCallbackExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("gRPC callback executor didn't terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
Nasıl Çalıştırılır ve Test Edilir?
gRPC Servisini Çalıştırın: Bu örneği test etmek için, localhost:50051 adresinde çalışan basit bir gRPC sunucusuna ihtiyacınız var. Gerekirse bu sunucuyu ayrı bir projede veya manuel olarak yazmanız gerekecektir.

Java

// Basit bir gRPC Sunucusu (Örnek)
// Bu kod 'MyUnifiedApplication' projenizin dışında veya ayrı bir 'server' modülünde olmalı.

import com.yourcompany.myapp.grpc.MyServiceGrpc;
import com.yourcompany.myapp.grpc.MyServiceProto;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyGrpcServer {
private static final Logger logger = LoggerFactory.getLogger(MyGrpcServer.class);
private Server server;

    public void start() throws IOException {
        int port = 50051;
        server = ServerBuilder.forPort(port)
            .addService(new MyServiceImpl())
            .build()
            .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                MyGrpcServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class MyServiceImpl extends MyServiceGrpc.MyServiceImplBase {
        @Override
        public void myMethod(MyServiceProto.MyRequest request, StreamObserver<MyServiceProto.MyResponse> responseObserver) {
            logger.info("Received gRPC request: " + request.getName());
            MyServiceProto.MyResponse reply = MyServiceProto.MyResponse.newBuilder()
                .setMessage("Hello " + request.getName() + " from gRPC Server!")
                .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final MyGrpcServer server = new MyGrpcServer();
        server.start();
        server.blockUntilShutdown();
    }
}
Ethernet Servisini Simüle Edin (İsteğe Bağlı): localhost:12345 üzerinde bir TCP sunucusu çalıştırarak Ethernet bağlantısını simüle edebilirsiniz. Python ile basit bir örnek:

Python

import socket

HOST = '127.0.0.1'
PORT = 12345

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
s.bind((HOST, PORT))
s.listen()
print(f"Ethernet server listening on {HOST}:{PORT}")
conn, addr = s.accept()
with conn:
print(f"Connected by {addr}")
while True:
data = conn.recv(1024)
if not data:
break
print(f"Received from Ethernet: {data.decode()}")
conn.sendall(b"ACK: " + data) # Yanıt gönder
Maven Build: Projenin kök dizininde mvn clean install komutunu çalıştırarak Protobuf sınıflarını oluşturun ve projeyi derleyin.

Uygulamayı Çalıştırın: MyUnifiedApplication sınıfını çalıştırın. Konsol çıktısında hem gRPC hem de Ethernet bağlantı ve iletişim mesajlarını görmelisiniz. Bağlantıları kesip tekrar denemeleri de izleyebilirsiniz.

Önemli Noktalar ve Geliştirmeler
Güncel Bağımlılıklar: pom.xml içindeki gRPC ve Protobuf versiyonlarını düzenli olarak kontrol edin ve güncel tutun.

Hata Yönetimi: Özellikle ConnectionException ve StatusRuntimeException gibi özel hata türlerini yakalayarak uygulamanızda daha spesifik hata işleme yapın.

Logging: slf4j ve tercih ettiğiniz bir logging framework'ü (Logback, Log4j2) kullanarak detaylı loglar tutun. Bu, sorun giderme ve izleme için hayati önem taşır.

Executor Yönetimi: Asenkron gRPC çağrılarının callback'leri için özel ExecutorService kullanmak, uygulamanızın ana iş parçacığının tıkanmasını önler. Bu ExecutorService'in uygulama kapanışında düzgünce kapatıldığından emin olun.

Güvenlik (TLS/SSL): Production ortamında gRPC için mutlaka TLS/SSL kullanın (.usePlaintext() yerine .useTransportSecurity() ve sertifika yönetimi).

Konfigürasyon: Microservice ve Ethernet cihaz adreslerini, yeniden bağlantı gecikmelerini ve deneme sayılarını hardcode etmek yerine harici bir konfigürasyon dosyasından (örneğin, application.properties veya yaml) okuyun.

Devre Kesici (Circuit Breaker): Sürekli başarısız olan bağlantı denemeleri için Circuit Breaker desenini (örneğin, Resilience4j gibi bir kütüphane ile) uygulayarak dış servislerinize aşırı yüklenmeyi önleyebilirsiniz.

Metrikler ve İzleme: Bağlantı durumları, RPC çağrı sayıları, gecikmeler gibi metrikleri toplamak ve izleme sistemlerinize (Prometheus, Grafana, Micrometer) göndermek, sisteminizin sağlığını anlamak için önemlidir.

Bu birleştirilmiş örnek, kütüphanenizin nasıl genel, yeniden kullanılabilir ve güçlü olabileceğini gösteriyor. Her iki bağlantı türü için de ortak bir API ve yaşam döngüsü yönetimi sunarak kod tekrarını azaltır ve gelecekte yeni bağlantı türleri eklemenizi kolaylaştırır.
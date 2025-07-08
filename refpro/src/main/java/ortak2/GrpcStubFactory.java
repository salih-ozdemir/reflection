package ortak2;

// Define this factory interface within your common library, perhaps in the same package as ManagedConnection
@FunctionalInterface
public interface GrpcStubFactory<T extends io.grpc.stub.AbstractStub<T>> {
    T create(io.grpc.Channel channel);
}
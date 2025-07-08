package ortak3;

import io.grpc.Channel;
import io.grpc.stub.AbstractStub;

@FunctionalInterface
public interface GrpcStubFactory<T extends AbstractStub<T>> {
    T create(Channel channel);
}
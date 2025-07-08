package ortak4;


public class GrpcStubPool<T extends AbstractStub<T>> {
    private final BlockingQueue<T> pool;
    private final Supplier<T> stubFactory;

    public GrpcStubPool(int size, Supplier<T> stubFactory) {
        this.pool = new ArrayBlockingQueue<>(size);
        this.stubFactory = stubFactory;
        initializePool(size);
    }

    private void initializePool(int size) {
        for (int i = 0; i < size; i++) {
            pool.add(stubFactory.get());
        }
    }

    public T borrowStub() throws InterruptedException {
        return pool.take();
    }

    public void returnStub(T stub) {
        if (stub != null) {
            pool.offer(stub);
        }
    }
}
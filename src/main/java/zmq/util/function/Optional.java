package zmq.util.function;

public class Optional<T>
{
    private final T value;

    private Optional(T value)
    {
        this.value = value;
    }

    public void ifPresent(Consumer<T> consumer)
    {
        if (value != null) {
            consumer.accept(value);
        }
    }

    public static <T> Optional<T> ofNullable(T value)
    {
        return new Optional<>(value);
    }
}

package zmq.util.function;

/**
 Represents an operation that accepts two input arguments and returns no result. This is the two-arity specialization of Consumer.
 Unlike most other functional interfaces, {@link BiConsumer} is expected to operate via side-effects.

 *
 * <p>This is a functional interface
 * whose functional method is {@link #accept(Object, Object)}.
 *
 * @param <T> the type of the input to the operation
 *
 */
public interface BiConsumer<T, U>
{
    /**
     * Performs this operation on the given argument.
     *
     * @param t the first input argument
     * @param t the second input argument
     */
    void accept(T t, U u);
}

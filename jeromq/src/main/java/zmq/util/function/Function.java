package zmq.util.function;

/**
 * Represents a function that accepts one argument and produces a result.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #apply(Object)}.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 *
 */
public interface Function<T, R>
{
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     */
    R apply(T t);
}

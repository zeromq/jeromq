package zmq.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Documents an API in DRAFT state.
 * All APIs marked with @Draft are subject to change at ANY time until declared stable.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface Draft {
}

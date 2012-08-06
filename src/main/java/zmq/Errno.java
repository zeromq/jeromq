package zmq;

import java.util.concurrent.atomic.AtomicReference;


public enum Errno {

    EINTR(4, "Interrupted system call"),
    EAGAIN(11, "Resource temporarily unavailable"),
    ENOMEM(12, "Cannot allocate memory"),
	//EFAULT(14, "Bad address"),
	//EINVAL(22, "Invalid argument"),
	EMFILE(24, "Too many files open"),
	EINPROGRESS(46, "Operation now in progress"),
	ENOTSOCK(48, "Socket operation on nonsocket"),
	EPROTONOSUPPORT(53, "Protocol not supported"),
	ECONNREFUSED(71, "Connection refused"),
	ENOCOMPATPROTO(156384712 + 52, "Not Compatible Protocol"),
	//ETERM(156384712 + 53, "Terminated"),
	EMTHREAD(156384712 + 54, "Thread Error");


	private int code;
	private String description;
	
	private Errno(int code, String description) {
		this.code = code;
		this.description = description;
	}
	private static final AtomicReference<Errno> errno = new AtomicReference<Errno>();

	public static class ErrnoException extends RuntimeException {

        private static final long serialVersionUID = 7545549762882375476L;
        
        Errno errno;
        public ErrnoException(Errno errno) {
            super(errno.toString());
            this.errno = errno;
        }
        
        public Errno getErrno() {
            return errno;
        }
	    
	}
	//private final int ZMQ_HAUSNUMERO = 156384712;

	@Override
	public String toString() {
		return String.valueOf(code) + " : " + description;
	}


	public static void set(int code) {
		errno.set(fromCode(code));
		throwException();
	}

	public static void set(Errno errno_) {
	    set(errno_, true);
	}

	public static void set(Errno errno_, boolean raise) {
        errno.set(errno_);
        if (raise && errno_ != EAGAIN) {
            throwException();
        }
    }

    public static Errno get() {
        return errno.get();
    }
	
    public static int getErrorCode() {
        return get().getCode();
    }
	
	public int getCode() {
	    return code;
	}
	
	public static void throwException() {
		throw new ErrnoException(get());
	}

	private static Errno fromCode(int code) {
		for(Errno e: Errno.values()) {
			if (e.code == code) return e;
		}
		return null;
	}


	public static void errno_assert(boolean cond) {
		if (!cond) {
			throwException();
		}
	}


    public static boolean is(Errno target) {
        return get() == target;
    }

}

/*
#define EPERM           1                // Operation not permitted
#define ENOENT          2                // No such file or directory
#define ESRCH           3                // No such process
#define EINTR           4                // Interrupted system call
#define EIO             5                // Input/output error
#define ENXIO           6                // Device not configured
#define E2BIG           7                // Argument list too long
#define ENOEXEC         8                // Exec format error
#define EBADF           9                // Bad file number
#define ECHILD          10               // No spawned processes
#define EAGAIN          11               // Resource temporarily unavailable
#define ENOMEM          12               // Cannot allocate memory
#define EACCES          13               // Access denied
#define EFAULT          14               // Bad address
#define ENOTBLK         15               // Not block device
#define EBUSY           16               // Device busy
#define EEXIST          17               // File exist
#define EXDEV           18               // Cross-device link
#define ENODEV          19               // Operation not supported by device
#define ENOTDIR         20               // Not a directory
#define EISDIR          21               // Is a directory
#define EINVAL          22               // Invalid argument
#define ENFILE          23               // Too many open files in system
#define EMFILE          24               // Too many files open
#define ENOTTY          25               // Inappropriate ioctl for device
#define ETXTBSY         26               // Unknown error
#define EFBIG           27               // File too large
#define ENOSPC          28               // No space left on device
#define ESPIPE          29               // Illegal seek
#define EROFS           30               // Read-only file system
#define EMLINK          31               // Too many links
#define EPIPE           32               // Broken pipe
#define EDOM            33               // Numerical arg out of domain
#define ERANGE          34               // Result too large
#define EUCLEAN         35               // Structure needs cleaning
#define EDEADLK         36               // Resource deadlock avoided
#define EUNKNOWN        37               // Unknown error
#define ENAMETOOLONG    38               // File name too long
#define ENOLCK          39               // No locks available
#define ENOSYS          40               // Function not implemented
#define ENOTEMPTY       41               // Directory not empty
#define EILSEQ          42               // Invalid multibyte sequence

//
// Sockets errors
//

#define EWOULDBLOCK     45               // Operation would block
#define EINPROGRESS     46               // Operation now in progress
#define EALREADY        47               // Operation already in progress
#define ENOTSOCK        48               // Socket operation on nonsocket
#define EDESTADDRREQ    49               // Destination address required
#define EMSGSIZE        50               // Message too long
#define EPROTOTYPE      51               // Protocol wrong type for socket
#define ENOPROTOOPT     52               // Bad protocol option
#define EPROTONOSUPPORT 53               // Protocol not supported
#define ESOCKTNOSUPPORT 54               // Socket type not supported
#define EOPNOTSUPP      55               // Operation not supported
#define EPFNOSUPPORT    56               // Protocol family not supported
#define EAFNOSUPPORT    57               // Address family not supported 
#define EADDRINUSE      58               // Address already in use
#define EADDRNOTAVAIL   59               // Cannot assign requested address
#define ENETDOWN        60               // Network is down
#define ENETUNREACH     61               // Network is unreachable
#define ENETRESET       62               // Network dropped connection on reset
#define ECONNABORTED    63               // Connection aborted
#define ECONNRESET      64               // Connection reset by peer
#define ENOBUFS         65               // No buffer space available
#define EISCONN         66               // Socket is already connected
#define ENOTCONN        67               // Socket is not connected
#define ESHUTDOWN       68               // Cannot send after socket shutdown
#define ETOOMANYREFS    69               // Too many references
#define ETIMEDOUT       70               // Operation timed out
#define ECONNREFUSED    71               // Connection refused
#define ELOOP           72               // Cannot translate name
#define EWSNAMETOOLONG  73               // Name component or name was too long
#define EHOSTDOWN       74               // Host is down
#define EHOSTUNREACH    75               // No route to host
#define EWSNOTEMPTY     76               // Cannot remove a directory that is not empty
#define EPROCLIM        77               // Too many processes
#define EUSERS          78               // Ran out of quota
#define EDQUOT          79               // Ran out of disk quota
#define ESTALE          80               // File handle reference is no longer available
#define EREMOTE         81               // Item is not available locally

//
// Resolver errors
//

#define EHOSTNOTFOUND   82               // Host not found
#define ETRYAGAIN       83               // Nonauthoritative host not found
#define ENORECOVERY     84               // A nonrecoverable error occured
#define ENODATA         85               // Valid name, no data record of requested type

//
// Misc. error codes
//

#define EPROTO          86               // Protocol error
#define ECHKSUM         87               // Checksum error
#define EBADSLT         88               // Invalid slot
#define EREMOTEIO       89               // Remote I/O error

//
// Error code aliases
//

#define ETIMEOUT        ETIMEDOUT
#define EBUF            ENOBUFS
#define EROUTE          ENETUNREACH
#define ECONN           ENOTCONN
#define ERST            ECONNRESET
#define EABORT          ECONNABORTED

#define EFSM (ZMQ_HAUSNUMERO + 51)
#define ENOCOMPATPROTO (ZMQ_HAUSNUMERO + 52)
#define ETERM (ZMQ_HAUSNUMERO + 53)
#define EMTHREAD (ZMQ_HAUSNUMERO + 54)
*/

package app.mystery0.nodeflow.core.common

class NodeFlowException(
    val kind: Kind,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Kind {
        Network,
        Http,
        NotFound,
        EmptyBody,
        Parse,
        AccessDenied,
        Auth,
        Unknown,
    }
}

fun Throwable.isAccessDenied(): Boolean =
    this is NodeFlowException &&
        kind == NodeFlowException.Kind.AccessDenied

fun Throwable.isNotFound(): Boolean =
    this is NodeFlowException &&
        kind == NodeFlowException.Kind.NotFound

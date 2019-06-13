#include "corda-core.h"

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
#pragma ide diagnostic ignored "cert-err58-cpp"
net::corda::TypeRegistration PublicKeyRegistration("java.security.PublicKey", [](proton::codec::decoder &decoder) {
    return new java::security::PublicKey(decoder);
});

net::corda::TypeRegistration InstantRegistration("java.time.Instant", [](proton::codec::decoder &decoder) {
    return new java::time::Instant(decoder);
});

net::corda::TypeRegistration LocalDateRegistration("java.time.LocalDate", [](proton::codec::decoder &decoder) {
    return new java::time::LocalDate(decoder);
});

net::corda::TypeRegistration ClassRegistration("java.lang.Class", [](proton::codec::decoder &decoder) {
    return new java::lang::Class<net::corda::Any>(decoder);
});

net::corda::TypeRegistration ByteSequenceRegistration("net.corda:0UvJuq940P0jrySmql4EPg==", [](proton::codec::decoder &decoder) {
    return new net::corda::core::utilities::ByteSequence(decoder);
}); // NOLINT(cert-err58-cpp)

net::corda::TypeRegistration OpaqueBytesRegistration("net.corda:pgT0Kc3t/bvnzmgu/nb4Cg==", [](proton::codec::decoder &decoder) {
    return new net::corda::core::utilities::OpaqueBytes(decoder);
}); // NOLINT(cert-err58-cpp)

#pragma clang diagnostic pop

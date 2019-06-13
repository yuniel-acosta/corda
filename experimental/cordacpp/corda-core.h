#ifndef CORDACPP_CORDA_STD_SERIALISERS_H
#define CORDACPP_CORDA_STD_SERIALISERS_H

#include "corda-serialization.h"

namespace net {
namespace corda {

/**
 * An enum, for which each property corresponds to a transaction component group. The position in the enum class
 * declaration (ordinal) is used for component-leaf ordering when computing the Merkle tree.
 */
enum ComponentGroupEnum {
    INPUTS, // ordinal = 0.
    OUTPUTS, // ordinal = 1.
    COMMANDS, // ordinal = 2.
    ATTACHMENTS, // ordinal = 3.
    NOTARY, // ordinal = 4.
    TIMEWINDOW, // ordinal = 5.
    SIGNERS, // ordinal = 6.
    REFERENCES // ordinal = 7.
};

namespace core {

namespace utilities {

// ByteSequence is a weird class and it currently defeats proper handling of inherited types.
// TODO: Dig in to the apparent issue with abstract classes in LocalTypeInformationBuilder.kt

class ByteSequence : public net::corda::Any {
public:
    ByteSequence() = default;

    explicit ByteSequence(proton::codec::decoder &decoder) {
    }
};

class OpaqueBytes : public net::corda::core::utilities::ByteSequence {
public:
    OpaqueBytes() = default;

    explicit OpaqueBytes(proton::codec::decoder &decoder) : net::corda::core::utilities::ByteSequence(decoder) {}
};

}  // namespace utilities

//namespace serialization {
//
//// Deserialization of this class is specialised to allow a custom API for deserialize().
//template <class T> class SerializedBytes : public net::corda::core::utilities::OpaqueBytes {
//public:
//    proton::binary bytes;
//
//    SerializedBytes() = default;
//
//    explicit SerializedBytes(proton::codec::decoder &decoder) : net::corda::core::utilities::OpaqueBytes(decoder) {
//        net::corda::Parser::read_to(decoder, bytes);
//    }
//
//    ptr<T> deserialize() { return parse<T>(bytes); }
//};
//
//}  // namespace serialization

}  // namespace core
}  // namespace corda
}  // namespace net

// Standard serialisers that don't follow the regular AMQP format for various reasons.
// If you add a class here, register it in GenerateCPP.kt alreadyImplemented list.

namespace java {

namespace lang {

template <class T>
class Class : public net::corda::Any {
public:
    std::string name;

    explicit Class(proton::codec::decoder &decoder) {
        decoder >> name;
    }
};

class Throwable : public net::corda::Any {
public:
    explicit Throwable(proton::codec::decoder &decoder) {
        throw std::runtime_error("Deserialisation of exceptions is not implemented.");
    }
};

}  // lang

namespace util {

class Currency : public net::corda::Any {
public:
    std::string code;

    explicit Currency(proton::codec::decoder &decoder) {
        decoder >> code;
    }
};

}  // util

namespace security {

class PublicKey : public net::corda::Any {
public:
    proton::binary x509_bits;

    PublicKey() = default;

    explicit PublicKey(proton::codec::decoder &decoder) {
        proton::codec::start s;
        decoder >> s;
        proton::symbol sym;
        decoder >> sym;
        if (sym != "net.corda:java.security.PublicKey")
            throw std::invalid_argument(net::corda::msg() << "Stream contains symbol '" << sym << "' but expected a java.security.PublicKey");
        decoder >> x509_bits;
        decoder >> proton::codec::finish();
    }
};


}  // security

namespace time {

class Instant : public net::corda::Any {
public:
    int64_t epoch_seconds;
    int32_t nanos;

    Instant() : epoch_seconds(0), nanos(0) {}

    explicit Instant(proton::codec::decoder &decoder) : epoch_seconds(0), nanos(0) {
        decoder >> epoch_seconds;
        decoder >> nanos;
    }
};

class LocalDate : public net::corda::Any {
public:
    uint32_t year;
    uint8_t month;
    uint8_t day;

    LocalDate() = default;

    explicit LocalDate(proton::codec::decoder &decoder) {
        decoder >> year;
        decoder >> month;
        decoder >> day;
    }
};

class Duration : public net::corda::Any {
public:
    uint64_t seconds{};
    uint32_t nanos{};

    Duration() = default;

    explicit Duration(proton::codec::decoder &decoder) {
        decoder >> seconds;
        decoder >> nanos;
    }
};

}
}

#endif //CORDACPP_CORDA_STD_SERIALISERS_H

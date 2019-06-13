#include "corda-serialization.h"
#include "all-messages.h"

#include <iostream>
#include <fstream>
#include <sstream>

using namespace std;
using namespace net::corda;

namespace transactions = net::corda::core::transactions;
namespace contracts = net::corda::core::contracts;

string load_file(const char *filename) {
    ifstream stream("stx");
    return string((istreambuf_iterator<char>(stream)), (istreambuf_iterator<char>()));
}

template <class T>
ptr<net::corda::core::serialization::SerializedBytes<ptr<T>>> &operator>>(ptr<net::corda::core::serialization::SerializedBytes<ptr<T>>> &b, ptr<T> &o) {
    o = parse<T>(b->bytes);
    return b;
}

int main() {
    string stx_bits = load_file("stx");

    cout << dump(stx_bits) << endl;

    auto stx = parse<transactions::SignedTransaction>(stx_bits);
    ptr<transactions::CoreTransaction> wtx;
    stx->tx_bits >> wtx;

//    ifstream stream("/tmp/wtx");
//    string bits = string((istreambuf_iterator<char>(stream)), (istreambuf_iterator<char>()));
//
//    if (bits.empty()) {
//        cerr << "Failed to read file" << endl;
//        return 1;
//    }
//
//    cout << dump(bits) << endl;
//    auto wtx = parse<transactions::WireTransaction>(bits);
//    cout << "This wtx has " << wtx->component_groups.size() << " component groups." << endl;
//    cout << "The privacy salt is " << wtx->privacy_salt->bytes.size() << " bytes long." << endl;
//
//    auto inputs = wtx->component_groups[ComponentGroupEnum::INPUTS]->components;
//    auto outputs = wtx->component_groups[ComponentGroupEnum::OUTPUTS]->components;
//    cout << "There are " << inputs.size() << " inputs and " << outputs.size() << " outputs." << endl;
//
//    int out_index = 0;
//    for (auto &out_slot : outputs) {
//        auto output = parse<contracts::TransactionState<contracts::ContractState>>(dynamic_cast<core::serialization::SerializedBytes<Any>*>(out_slot.get())->bytes);
//        cout << "  Output " << out_index++ << " is governed by contract " << output->contract << endl;
//    }

    return 0;
}

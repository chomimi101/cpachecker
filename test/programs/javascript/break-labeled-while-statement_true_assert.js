function __VERIFIER_error() {}

var x = 0;
var y = 0;

outer:
    while (x < 3) {
      ++x;
      while (y < 10) {
        ++y;
        break outer;
        __VERIFIER_error();
      }
    }

if (x !== 1) {
  __VERIFIER_error();
}

if (y !== 1) {
  __VERIFIER_error();
}

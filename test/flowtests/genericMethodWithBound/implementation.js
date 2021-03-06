/*
declare module foo {
    declare export interface Bound {
        marker: true;
    }
    declare export interface Foo {
        gen<T: Bound>(t: T): Bar<T>;
    }
    declare export interface Bar<T> {
        value: T;
    }
    declare export var foo: Foo;
}

*/

module.exports = {
    foo: {
        gen: function (t) {
            if (!t.marker) {
                return "TypeError!";
            }
            return {
                value: false// <- The error is here!
            };
        }
    }
}
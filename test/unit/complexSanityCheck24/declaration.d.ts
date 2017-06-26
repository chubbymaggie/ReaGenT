declare function chain<T>(): _Chain<T>;
interface _Chain<T> {
    groupBy: _ChainOfArrays<T>;
    value: T;
}
interface _ChainOfArrays<T> extends _Chain<T[]> {}
// Type definitions for rx-lite-time 4.0
// Project: https://github.com/Reactive-Extensions/RxJS
// Definitions by: Carl de Billy <http://carl.debilly.net/>, Igor Oleinikov <https://github.com/Igorbek>
// Definitions: https://github.com/DefinitelyTyped/DefinitelyTyped

///<reference path="../rx-lite/index.d.ts" />

declare namespace Rx {
    export interface Observable<T> {
        delaySubscription(dueTime: number, scheduler?: IScheduler): Observable<T>;
        delayWithSelector(delayDurationSelector: (item: T) => number): Observable<T>;
        delayWithSelector(subscriptionDelay: number, delayDurationSelector: (item: T) => number): Observable<T>;

        timeoutWithSelector<TTimeout>(firstTimeout: Observable<TTimeout>, timeoutdurationSelector?: (item: T) => Observable<TTimeout>, other?: Observable<T>): Observable<T>;

        debounceWithSelector<TTimeout>(debounceDurationSelector: (item: T) => Observable<TTimeout>): Observable<T>;
        /**
        * @deprecated use #debounceWithSelector instead.
        */
        throttleWithSelector<TTimeout>(debounceDurationSelector: (item: T) => Observable<TTimeout>): Observable<T>;

        skipLastWithTime(duration: number, scheduler?: IScheduler): Observable<T>;
        takeLastWithTime(duration: number, timerScheduler?: IScheduler, loopScheduler?: IScheduler): Observable<T>;

        takeLastBufferWithTime(duration: number, scheduler?: IScheduler): Observable<T[]>;
        takeWithTime(duration: number, scheduler?: IScheduler): Observable<T>;
        skipWithTime(duration: number, scheduler?: IScheduler): Observable<T>;

        skipUntilWithTime(startTime: Date, scheduler?: IScheduler): Observable<T>;
        skipUntilWithTime(duration: number, scheduler?: IScheduler): Observable<T>;
        takeUntilWithTime(endTime: Date, scheduler?: IScheduler): Observable<T>;
        takeUntilWithTime(duration: number, scheduler?: IScheduler): Observable<T>;

        windowWithTime(timeSpan: number, timeShift: number, scheduler?: IScheduler): Observable<Observable<T>>;
        windowWithTime(timeSpan: number, scheduler?: IScheduler): Observable<Observable<T>>;
        windowWithTimeOrCount(timeSpan: number, count: number, scheduler?: IScheduler): Observable<Observable<T>>;
        bufferWithTime(timeSpan: number, timeShift: number, scheduler?: IScheduler): Observable<T[]>;
        bufferWithTime(timeSpan: number, scheduler?: IScheduler): Observable<T[]>;
        bufferWithTimeOrCount(timeSpan: number, count: number, scheduler?: IScheduler): Observable<T[]>;
    }

    interface ObservableStatic {
        timer(dueTime: Date, period: number, scheduler?: IScheduler): Observable<number>;
        timer(dueTime: Date, scheduler?: IScheduler): Observable<number>;
    }
}

declare module "rx-lite-time" {
    export = Rx;
}

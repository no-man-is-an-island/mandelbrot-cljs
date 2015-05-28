# Mandelbrot CLJS

A (mostly!) clojurescript interactive [Mandelbrot Set](http://en.wikipedia.org/wiki/Mandelbrot_set) visualisation using a HTML5 canvas.

The demo can be found at [http://mandelbrot.davidwilliams.london](http://mandelbrot.davidwilliams.london)

## Technical Stuff

I cribbed some help with the sometimes impenetrable Canvas API (and found the [iteration smoothing technique](http://linas.org/art-gallery/escape/escape.html) for extra prettiness) in [cslarsen's javascript mandelbrot implementation](https://github.com/cslarsen/mandelbrot-js). Almost everything else has been done from first principles though.

I started with a pure clojurescript implementation, but I couldn't speed it up sufficiently without mutable state. I played around with [swannodette's macros for this](https://github.com/swannodette/chambered/blob/master/src/chambered/macros.clj) which use arrays of length one as mutable state, but it was still around 10 times slower than a pure javascript version. Hence, the 'hard maths' of iterating the complex number operations is done in a small javascript library and the user interaction, re-rendering etc. is done in clojurescript.

The number of iterations (i.e. the 'precision' of the rendering) is set to increase logarithmically with the 'level' of zoom (i.e. the number of pixels a distance of 1 in the complex plane takes up). I don't know if there's a theoretical basis for this, but it seems to work quite well. If you zoom in enough the limits of floating point precision break the visualisation.

## License

Copyright © 2015 David Williams

Distributed under the Eclipse Public License, the same as Clojure.

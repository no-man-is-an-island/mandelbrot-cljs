function smoothed_count(
    iterations,
    final_modulus_squared){

    final_modulus = Math.sqrt(final_modulus_squared);

    if (final_modulus< 1){
        return iterations
    }
    else{

        return iterations - (Math.log(Math.log(final_modulus)) / Math.log(2))
    }
}

function mandelbrot_smoothed_iteration_count(
    escape_radius_squared,
    max_iterations,
    initial_real,
    initial_imaginary){

    var mod_z = (initial_real * initial_real) + (initial_imaginary * initial_imaginary);

    var real = initial_real;
    var imaginary = initial_imaginary;

    var iterations = 0;

    while (mod_z < escape_radius_squared && iterations != max_iterations){

        var new_real = (real * real) - (imaginary * imaginary) + initial_real;
        imaginary = (2 * real * imaginary) + initial_imaginary;
        real = new_real;
        mod_z = (real * real) + (imaginary * imaginary);
        iterations++;
    }

    return smoothed_count(iterations, mod_z)
}

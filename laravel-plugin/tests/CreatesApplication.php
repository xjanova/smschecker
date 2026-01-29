<?php

namespace Tests;

trait CreatesApplication
{
    /**
     * Creates the application.
     *
     * Override in subclasses if needed for package testing.
     */
    public function createApplication()
    {
        // For standalone testing, use Orchestra Testbench
        // For integration testing within a Laravel app, use the app's bootstrap
        $app = require __DIR__ . '/../vendor/orchestra/testbench-core/laravel/bootstrap/app.php';
        $app->make(\Illuminate\Contracts\Console\Kernel::class)->bootstrap();

        return $app;
    }
}

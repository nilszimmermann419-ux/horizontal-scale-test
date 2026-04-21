package util

import (
	"context"
	"fmt"
	"math"
	"time"
)

// RetryOptions configures retry behavior
type RetryOptions struct {
	MaxRetries  int
	BaseDelay   time.Duration
	MaxDelay    time.Duration
	Multiplier  float64
	RetryableFn func(error) bool // optional predicate to decide if an error is retryable
}

// DefaultRetryOptions returns sensible defaults
func DefaultRetryOptions() RetryOptions {
	return RetryOptions{
		MaxRetries: 3,
		BaseDelay:  500 * time.Millisecond,
		MaxDelay:   30 * time.Second,
		Multiplier: 2.0,
		RetryableFn: func(err error) bool {
			return err != nil
		},
	}
}

// Retry executes fn with exponential backoff until it succeeds, max retries are reached,
// or the context is cancelled
func Retry(ctx context.Context, fn func() error, opts RetryOptions) error {
	if opts.RetryableFn == nil {
		opts.RetryableFn = DefaultRetryOptions().RetryableFn
	}

	var lastErr error
	for attempt := 0; attempt <= opts.MaxRetries; attempt++ {
		if err := ctx.Err(); err != nil {
			return fmt.Errorf("retry cancelled: %w", err)
		}

		if err := fn(); err == nil {
			return nil
		} else if !opts.RetryableFn(err) {
			return err
		}

		lastErr = err

		if attempt < opts.MaxRetries {
			delay := calculateBackoff(attempt, opts.BaseDelay, opts.MaxDelay, opts.Multiplier)
			timer := time.NewTimer(delay)
			select {
			case <-ctx.Done():
				timer.Stop()
				return fmt.Errorf("retry cancelled during backoff: %w", ctx.Err())
			case <-timer.C:
			}
		}
	}

	return fmt.Errorf("max retries (%d) exceeded: %w", opts.MaxRetries, lastErr)
}

// RetryWithBackoff is a convenience function for simple retry scenarios
func RetryWithBackoff(ctx context.Context, fn func() error, maxRetries int, baseDelay time.Duration) error {
	opts := RetryOptions{
		MaxRetries: maxRetries,
		BaseDelay:  baseDelay,
		MaxDelay:   30 * time.Second,
		Multiplier: 2.0,
	}
	return Retry(ctx, fn, opts)
}

func calculateBackoff(attempt int, baseDelay, maxDelay time.Duration, multiplier float64) time.Duration {
	backoff := float64(baseDelay) * math.Pow(multiplier, float64(attempt))
	if backoff > float64(maxDelay) {
		backoff = float64(maxDelay)
	}
	return time.Duration(backoff)
}

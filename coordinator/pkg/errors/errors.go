package errors

import (
	"errors"
	"fmt"
)

// Sentinel errors for the coordinator domain
var (
	// ErrShardNotFound is returned when a requested shard does not exist in the registry
	ErrShardNotFound = errors.New("shard not found")

	// ErrRegionNotAllocated is returned when a region has no assigned shard
	ErrRegionNotAllocated = errors.New("region not allocated")

	// ErrCoordinatorUnavailable is returned when the coordinator service is not reachable
	ErrCoordinatorUnavailable = errors.New("coordinator unavailable")

	// ErrInvalidRequest is returned when a request fails validation or contains bad parameters
	ErrInvalidRequest = errors.New("invalid request")
)

// Error represents a domain error with additional context
type Error struct {
	Op  string // operation being performed
	Err error  // underlying error
}

func (e *Error) Error() string {
	return fmt.Sprintf("%s: %v", e.Op, e.Err)
}

func (e *Error) Unwrap() error {
	return e.Err
}

// Wrap wraps an error with the operation context
func Wrap(err error, op string) error {
	if err == nil {
		return nil
	}
	return &Error{Op: op, Err: err}
}

// Is reports whether err is or wraps target
func Is(err, target error) bool {
	return errors.Is(err, target)
}

// As finds the first error in err's chain that matches target
func As(err error, target interface{}) bool {
	return errors.As(err, target)
}

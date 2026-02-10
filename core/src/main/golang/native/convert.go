package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"
	"runtime"
	"unsafe"

	"cfa/native/config"
)

//export detectProfileFormat
func detectProfileFormat(content C.c_string) *C.char {
	contentStr := C.GoString(content)
	format := config.DetectFormat(contentStr)
	return C.CString(string(format))
}

//export convertProfileToClash
func convertProfileToClash(completable unsafe.Pointer, content C.c_string, sourceFormat C.c_string) {
	go func(content, format string) {
		var result *config.ConvertResult
		var err error

		if format == "" || format == "auto" {
			// Auto-detect format
			result, err = config.ConvertToClash(content, config.FormatUnknown)
		} else {
			// Use specified format
			result, err = config.ConvertToClash(content, config.ProfileFormat(format))
		}

		// Marshal result to JSON
		var resultJSON string
		if err != nil && result == nil {
			// Create error result
			result = &config.ConvertResult{
				Success: false,
				Error:   err.Error(),
			}
		}

		jsonBytes, marshalErr := json.Marshal(result)
		if marshalErr != nil {
			resultJSON = `{"success":false,"error":"failed to marshal result"}`
		} else {
			resultJSON = string(jsonBytes)
		}

		C.complete(completable, marshalString(resultJSON))
		C.release_object(completable)

		runtime.GC()
	}(C.GoString(content), C.GoString(sourceFormat))
}

//export validateClashProfile
func validateClashProfile(content C.c_string) *C.char {
	contentStr := C.GoString(content)

	result, err := config.ConvertToClash(contentStr, config.FormatClash)
	if err != nil {
		return C.CString(`{"success":false,"error":"` + err.Error() + `"}`)
	}

	jsonBytes, err := json.Marshal(result)
	if err != nil {
		return C.CString(`{"success":false,"error":"failed to marshal result"}`)
	}

	return C.CString(string(jsonBytes))
}

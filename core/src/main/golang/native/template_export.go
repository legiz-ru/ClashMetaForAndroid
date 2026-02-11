package main

/*
#include <stdlib.h>
*/
import "C"
import (
	"cfa/native/config"
	"encoding/json"
	"unsafe"
)

//export GetAvailableTemplates
func GetAvailableTemplates() *C.char {
	templates := config.GetAvailableTemplates()

	jsonData, err := json.Marshal(templates)
	if err != nil {
		return C.CString(`{"error": "failed to marshal templates"}`)
	}

	return C.CString(string(jsonData))
}

//export GetCurrentTemplate
func GetCurrentTemplate(profilePath *C.char) *C.char {
	path := C.GoString(profilePath)

	templateID, err := config.GetCurrentTemplate(path)
	if err != nil {
		return C.CString(string(config.TemplateDefault))
	}

	return C.CString(string(templateID))
}

//export SetCurrentTemplate
func SetCurrentTemplate(profilePath *C.char, templateID *C.char) *C.char {
	path := C.GoString(profilePath)
	id := config.TemplateID(C.GoString(templateID))

	if err := config.SetCurrentTemplate(path, id); err != nil {
		return C.CString(err.Error())
	}

	return nil
}

//export ApplyTemplateToProfile
func ApplyTemplateToProfile(profilePath *C.char, templateContent *C.char, proxiesJSON *C.char) *C.char {
	path := C.GoString(profilePath)
	content := C.GoString(templateContent)
	proxiesStr := C.GoString(proxiesJSON)

	// Parse proxies JSON
	var proxies []map[string]interface{}
	if err := json.Unmarshal([]byte(proxiesStr), &proxies); err != nil {
		return C.CString("failed to parse proxies JSON: " + err.Error())
	}

	// Apply template
	if err := config.ApplyTemplateToProfile(path, content, proxies); err != nil {
		return C.CString(err.Error())
	}

	return nil
}

//export ValidateTemplateYAML
func ValidateTemplateYAML(content *C.char) *C.char {
	yamlContent := C.GoString(content)

	if err := config.ValidateTemplateYAML(yamlContent); err != nil {
		return C.CString(err.Error())
	}

	return nil
}

//export FreeString
func FreeString(str *C.char) {
	C.free(unsafe.Pointer(str))
}
